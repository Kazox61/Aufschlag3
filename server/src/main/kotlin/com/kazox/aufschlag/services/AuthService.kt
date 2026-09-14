package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.auth.LoginRequest
import com.kazox.aufschlag.api.auth.RegisterRequest
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.auth.UserResponse
import com.kazox.aufschlag.auth.RegistrationRules
import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.db.isUniqueViolation
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.mail.Mailer
import com.kazox.aufschlag.repositories.AuthIdentityRepository
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.PasswordResetTokenRepository
import com.kazox.aufschlag.repositories.RefreshTokenRepository
import com.kazox.aufschlag.repositories.UserRepository
import com.kazox.aufschlag.security.JwtService
import com.kazox.aufschlag.security.PasswordHasher
import com.kazox.aufschlag.security.Tokens
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

class AuthService(
    private val db: Database,
    private val users: UserRepository,
    private val identities: AuthIdentityRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val resetTokens: PasswordResetTokenRepository,
    private val memberships: MembershipRepository,
    private val bookings: BookingRepository,
    private val hasher: PasswordHasher,
    private val jwt: JwtService,
    private val backoff: LoginBackoff,
    private val mailer: Mailer,
    private val config: AuthConfig,
    /** Outlives the request: mail sends must not block (or fail) the response. */
    private val mailScope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
) {
    /** Presented-token-hash → pair issued for it; serves idempotent retries in the grace window. */
    private val gracePairs = ConcurrentHashMap<String, CachedPair>()

    private data class CachedPair(val at: Instant, val pair: TokenPairResponse)

    suspend fun register(request: RegisterRequest): TokenPairResponse {
        val email = request.email.normalizeEmail()
        validateEmail(email)
        validateName(request.name)
        validatePassword(request.password)
        // Existing account with this email — ANY provider — must 409: attaching a password to
        // a social-only account would hand it to whoever knows the email. Checked before the
        // ~250 ms bcrypt hash so a duplicate email costs one cheap lookup, not CPU.
        if (withTransaction(db) { users.findByEmail(email) } != null) throw emailTaken()
        val passwordHash = hasher.hash(request.password)

        val userId = try {
            withTransaction(db) {
                if (users.findByEmail(email) != null) throw emailTaken() // registered meanwhile
                val userId = users.create(email, request.name.trim())
                identities.createEmailIdentity(userId, email, passwordHash)
                userId
            }
        } catch (e: Exception) {
            // concurrent register with the same email loses the citext unique index race
            if (e.isUniqueViolation()) throw emailTaken() else throw e
        }
        return issueNewFamily(userId)
    }

    suspend fun login(request: LoginRequest): TokenPairResponse {
        val email = request.email.normalizeEmail()
        backoff.blockedForSeconds(email)?.let { seconds ->
            throw ApiException(
                HttpStatusCode.TooManyRequests,
                ErrorCode.RATE_LIMITED,
                "Too many failed attempts. Try again in ${seconds}s",
            )
        }
        val identity = withTransaction(db) { identities.findEmailIdentity(email) }
        // verify against a dummy hash when there is no account: same error, comparable timing
        val verified = hasher.verify(request.password, identity?.passwordHash ?: hasher.dummyHash)
        if (identity == null || !verified) {
            backoff.recordFailure(email)
            throw ApiException(
                HttpStatusCode.Unauthorized,
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid email or password",
            )
        }
        backoff.recordSuccess(email)
        return issueNewFamily(identity.userId)
    }

    suspend fun refresh(rawToken: String): TokenPairResponse {
        val presentedHash = Tokens.sha256(rawToken)
        val now = clock()

        val outcome = withTransaction(db) {
            val claimed = refreshTokens.claimForRotation(presentedHash, now)
            when {
                claimed != null && claimed.expiresAt.isAfter(now) -> {
                    val successorId = Uuid.random()
                    val successorRaw = Tokens.generate()
                    refreshTokens.insert(
                        id = successorId,
                        userId = claimed.userId,
                        familyId = claimed.familyId,
                        tokenHash = Tokens.sha256(successorRaw),
                        expiresAt = now.plus(config.refreshTokenTtl.toJavaDuration()),
                    )
                    refreshTokens.setReplacedBy(claimed.id, successorId)
                    val pair = TokenPairResponse(
                        accessToken = jwt.issueAccessToken(claimed.userId, now),
                        refreshToken = successorRaw,
                        expiresInSeconds = config.accessTokenTtl.inWholeSeconds,
                    )
                    // Cached before commit on purpose: a concurrent retry of the same token
                    // can only take the GraceRetry path once this revocation is visible, i.e.
                    // after commit — so the entry must already exist by then. Should the
                    // commit fail, the entry is unreachable (the token stays unrevoked) and
                    // the next successful rotation overwrites it.
                    pruneGracePairs(now)
                    gracePairs[presentedHash] = CachedPair(now, pair)
                    RefreshOutcome.Rotated(pair)
                }

                claimed != null -> RefreshOutcome.Invalid // expired; already revoked by the claim

                else -> {
                    val row = refreshTokens.findByHash(presentedHash)
                    val revokedAt = row?.revokedAt
                    // a used successor means the client DID receive the rotation response,
                    // so a replay of its predecessor can't be a lost-response retry
                    val successorUnused = row?.replacedById
                        ?.let { refreshTokens.findById(it) }
                        ?.let { it.revokedAt == null }
                        ?: false
                    when {
                        row == null -> RefreshOutcome.Invalid
                        revokedAt != null &&
                            successorUnused &&
                            revokedAt.plus(config.graceWindow.toJavaDuration()).isAfter(now) ->
                            // rotated moments ago: almost certainly a client retry after a
                            // lost response, not theft — hand back the same replacement pair
                            RefreshOutcome.GraceRetry

                        else -> {
                            // replay of an old rotated token: stolen-token signal — kill
                            // every live token in the family
                            refreshTokens.revokeFamily(row.familyId, now)
                            RefreshOutcome.Invalid
                        }
                    }
                }
            }
        }

        return when (outcome) {
            is RefreshOutcome.Rotated -> outcome.pair

            RefreshOutcome.GraceRetry ->
                gracePairs[presentedHash]
                    ?.takeIf { it.at.plus(config.graceWindow.toJavaDuration()).isAfter(now) }
                    ?.pair
                    ?: throw invalidToken() // e.g. server restarted since rotation; client re-logins

            RefreshOutcome.Invalid -> throw invalidToken()
        }
    }

    suspend fun logout(rawToken: String) {
        val hash = Tokens.sha256(rawToken)
        val now = clock()
        withTransaction(db) {
            refreshTokens.findByHash(hash)?.let { refreshTokens.revokeFamily(it.familyId, now) }
        }
        // 204 either way — an unknown token leaks nothing and logout stays idempotent
    }

    suspend fun requestPasswordReset(email: String) {
        val normalized = email.normalizeEmail()
        val raw = Tokens.generate()
        val userId = withTransaction(db) {
            val identity = identities.findEmailIdentity(normalized) ?: return@withTransaction null
            resetTokens.insert(identity.userId, Tokens.sha256(raw), clock().plus(config.resetTokenTtl.toJavaDuration()))
            identity.userId
        }
        // mail send is fire-and-forget: awaiting the provider call would make known-email
        // responses measurably slower than unknown-email ones (enumeration via timing) and
        // couple this endpoint's latency to the mail provider; the Mailer logs failures
        if (userId != null) mailScope.launch { mailer.sendPasswordReset(normalized, raw) }
    }

    suspend fun confirmPasswordReset(rawToken: String, newPassword: String) {
        validatePassword(newPassword)
        val hash = Tokens.sha256(rawToken)
        val newHash = hasher.hash(newPassword)
        val now = clock()
        withTransaction(db) {
            val row = resetTokens.claim(hash, now) ?: throw invalidToken()
            if (!row.expiresAt.isAfter(now)) throw invalidToken()
            identities.updatePassword(row.userId, newHash)
            // a reset after suspected compromise must end every session
            refreshTokens.revokeAllForUser(row.userId, now)
        }
    }

    suspend fun me(userId: Uuid): UserResponse {
        val user = withTransaction(db) { users.findById(userId) }
            ?: throw ApiException.unauthorized("Account no longer exists")
        return UserResponse(id = user.id.toString(), email = user.email, name = user.name)
    }

    /** GDPR account deletion — requires the current password, not just the access JWT, so a
     *  stolen 15-minute bearer token alone can't delete an account. Blocked while the user is
     *  the sole OWNER of a non-archived club (reassign ownership first). Takes the same
     *  per-user advisory lock BookingService.create holds while booking, so a concurrent booking
     *  attempt either finishes and commits before this transaction starts its cancel-scan (and
     *  gets caught by it) or is blocked until this transaction's delete has committed — either
     *  way no booking can be created in the gap and survive as an anonymized "ghost" booking
     *  blocking a court forever. */
    suspend fun deleteAccount(userId: Uuid, password: String) {
        val identity = withTransaction(db) {
            val user = users.findById(userId) ?: throw ApiException.unauthorized("Account no longer exists")
            identities.findEmailIdentity(user.email)
        }
        // bcrypt is CPU-bound and suspends onto Dispatchers.Default — never inside a transaction
        if (identity == null || !hasher.verify(password, identity.passwordHash)) {
            throw ApiException.unauthorized("Incorrect password")
        }
        withTransaction(db) {
            if (users.findById(userId) == null) throw ApiException.unauthorized("Account no longer exists")
            if (memberships.isSoleActiveOwner(userId)) {
                throw ApiException.forbidden("Transfer club ownership before deleting your account")
            }
            bookings.lockForBookingLimitCheck(userId)
            bookings.cancelAllFutureForUser(userId, clock())
            users.delete(userId)
        }
    }

    private suspend fun issueNewFamily(userId: Uuid): TokenPairResponse {
        val raw = Tokens.generate()
        val now = clock()
        val id = Uuid.random()
        withTransaction(db) {
            // family_id = id of the session's first token
            refreshTokens.insert(id, userId, familyId = id, Tokens.sha256(raw), now.plus(config.refreshTokenTtl.toJavaDuration()))
        }
        return TokenPairResponse(
            accessToken = jwt.issueAccessToken(userId, now),
            refreshToken = raw,
            expiresInSeconds = config.accessTokenTtl.inWholeSeconds,
        )
    }

    private fun pruneGracePairs(now: Instant) {
        if (gracePairs.size < GRACE_CACHE_PRUNE_SIZE) return
        val cutoff = now.minus(config.graceWindow.toJavaDuration())
        gracePairs.entries.removeIf { it.value.at.isBefore(cutoff) }
    }

    private sealed interface RefreshOutcome {
        data class Rotated(val pair: TokenPairResponse) : RefreshOutcome
        data object GraceRetry : RefreshOutcome
        data object Invalid : RefreshOutcome
    }

    private fun emailTaken() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.EMAIL_TAKEN, "This email is already registered")

    private fun invalidToken() =
        ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_TOKEN, "Invalid or expired token")

    private fun validateEmail(email: String) {
        if (email.length > RegistrationRules.EMAIL_MAX_LENGTH || !email.matches(RegistrationRules.EMAIL_REGEX)) {
            throw ApiException.validation("Invalid email address", field = "EMAIL")
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name.trim().length > RegistrationRules.NAME_MAX_LENGTH) {
            throw ApiException.validation("Name must be 1-${RegistrationRules.NAME_MAX_LENGTH} characters", field = "NAME")
        }
    }

    private fun validatePassword(password: String) {
        if (password.length !in RegistrationRules.PASSWORD_MIN_LENGTH..RegistrationRules.PASSWORD_MAX_LENGTH) {
            throw ApiException.validation(
                "Password must be ${RegistrationRules.PASSWORD_MIN_LENGTH}-${RegistrationRules.PASSWORD_MAX_LENGTH} characters",
                field = "PASSWORD",
            )
        }
    }

    companion object {
        private const val GRACE_CACHE_PRUNE_SIZE = 1000
    }
}

internal fun String.normalizeEmail(): String = trim().lowercase()
