package com.kazox.aufschlag

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
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(dataSource: DataSource, database: Database, auth: AuthConfig, mailer: Mailer): Module = module {
    single<DataSource> { dataSource }
    single<Database> { database }
    single<AuthConfig> { auth }
    single<Mailer> { mailer }

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

    // services
    singleOf(::HealthService)
    singleOf(::EntitlementService)
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
        )
    }
    single {
        ClubService(
            db = get(),
            clubs = get(),
            memberships = get(),
            users = get(),
            entitlements = get(),
        )
    }
    single {
        MembershipService(
            db = get(),
            clubs = get(),
            memberships = get(),
            users = get(),
            entitlements = get(),
            mailer = get(),
        )
    }
    single {
        CourtService(
            db = get(),
            clubs = get(),
            courts = get(),
            entitlements = get(),
        )
    }
    single {
        PriceRuleService(
            db = get(),
            clubs = get(),
            courts = get(),
            priceRules = get(),
            entitlements = get(),
        )
    }
    single {
        SlotAvailabilityService(
            db = get(),
            clubs = get(),
            courts = get(),
            priceRules = get(),
            memberships = get(),
            bookings = get(),
        )
    }
    single {
        BookingService(
            db = get(),
            clubs = get(),
            courts = get(),
            priceRules = get(),
            memberships = get(),
            bookings = get(),
            entitlements = get(),
        )
    }
}
