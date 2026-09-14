package com.kazox.aufschlag

import com.kazox.aufschlag.BackgroundScope
import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.mail.Mailer
import com.kazox.aufschlag.repositories.AuthIdentityRepository
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.PasswordResetTokenRepository
import com.kazox.aufschlag.repositories.PriceRuleRepository
import com.kazox.aufschlag.repositories.RefreshTokenRepository
import com.kazox.aufschlag.repositories.UserRepository
import com.kazox.aufschlag.repositories.postgres.PostgresAuthIdentityRepository
import com.kazox.aufschlag.repositories.postgres.PostgresBookingRepository
import com.kazox.aufschlag.repositories.postgres.PostgresClubRepository
import com.kazox.aufschlag.repositories.postgres.PostgresCourtRepository
import com.kazox.aufschlag.repositories.postgres.PostgresMembershipRepository
import com.kazox.aufschlag.repositories.postgres.PostgresPasswordResetTokenRepository
import com.kazox.aufschlag.repositories.postgres.PostgresPriceRuleRepository
import com.kazox.aufschlag.repositories.postgres.PostgresRefreshTokenRepository
import com.kazox.aufschlag.repositories.postgres.PostgresUserRepository
import com.kazox.aufschlag.security.JwtService
import com.kazox.aufschlag.security.PasswordHasher
import com.kazox.aufschlag.services.AuthService
import com.kazox.aufschlag.services.BookingService
import com.kazox.aufschlag.services.ClubService
import com.kazox.aufschlag.services.CourtService
import com.kazox.aufschlag.services.EntitlementService
import com.kazox.aufschlag.services.HealthService
import com.kazox.aufschlag.services.LoginBackoff
import com.kazox.aufschlag.services.MembershipService
import com.kazox.aufschlag.services.PriceRuleService
import com.kazox.aufschlag.services.SlotAvailabilityService
import com.kazox.aufschlag.services.TokenMaintenance
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import javax.sql.DataSource
import kotlin.time.Clock

fun appModule(dataSource: DataSource, database: Database, auth: AuthConfig, mailer: Mailer): Module = module {
    single<DataSource> { dataSource }
    single<Database> { database }
    single<AuthConfig> { auth }
    single<Mailer> { mailer }
    single<Clock> { Clock.System }
    // MembershipService (and AuthService) take a plain CoroutineScope for their mail sends;
    // the one BackgroundScope is drained on shutdown, so it must be the instance they get.
    single { BackgroundScope() } bind CoroutineScope::class

    // repositories
    single<UserRepository> { PostgresUserRepository() }
    single<AuthIdentityRepository> { PostgresAuthIdentityRepository() }
    single<RefreshTokenRepository> { PostgresRefreshTokenRepository() }
    single<PasswordResetTokenRepository> { PostgresPasswordResetTokenRepository() }
    single<ClubRepository> { PostgresClubRepository() }
    single<MembershipRepository> { PostgresMembershipRepository() }
    single<CourtRepository> { PostgresCourtRepository() }
    single<PriceRuleRepository> { PostgresPriceRuleRepository() }
    single<BookingRepository> { PostgresBookingRepository() }

    // security
    singleOf(::PasswordHasher)
    singleOf(::JwtService)
    single { LoginBackoff() }

    // services — singleOf resolves every constructor parameter by type. AuthService and
    // TokenMaintenance are spelled out because their trailing clock parameter is a defaulted
    // test seam, not an injectable type.
    singleOf(::HealthService)
    single { TokenMaintenance(db = get(), refreshTokens = get(), resetTokens = get()) }
    singleOf(::EntitlementService)
    singleOf(::ClubService)
    singleOf(::CourtService)
    singleOf(::PriceRuleService)
    singleOf(::SlotAvailabilityService)
    singleOf(::BookingService)
    single {
        AuthService(
            db = get(),
            users = get(),
            identities = get(),
            refreshTokens = get(),
            resetTokens = get(),
            memberships = get(),
            bookings = get(),
            hasher = get(),
            jwt = get(),
            backoff = get(),
            mailer = get(),
            config = get(),
            mailScope = get(),
        )
    }
    singleOf(::MembershipService)
}
