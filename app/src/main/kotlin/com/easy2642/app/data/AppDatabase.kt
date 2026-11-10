package com.easy2642.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM sponsor WHERE id = 1")
    fun sponsor(): Flow<SponsorEntity?>

    @Upsert
    suspend fun upsertSponsor(s: SponsorEntity)

    @Query("SELECT * FROM dependents ORDER BY firstName, lastName")
    fun dependents(): Flow<List<DependentEntity>>

    @Upsert
    suspend fun upsertDependent(d: DependentEntity)

    @Query("DELETE FROM dependents WHERE id = :id")
    suspend fun deleteDependent(id: String)

    @Query("DELETE FROM sponsor")
    suspend fun clearSponsor()

    @Query("DELETE FROM dependents")
    suspend fun clearDependents()
}

@Dao
interface ClaimDao {
    @Query("SELECT * FROM claims ORDER BY dateOfService DESC, id")
    fun claims(): Flow<List<ClaimEntity>>

    @Query("SELECT * FROM claims WHERE id = :id")
    suspend fun byId(id: String): ClaimEntity?

    @Upsert
    suspend fun upsert(c: ClaimEntity)

    @Query("DELETE FROM claims WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM claims")
    suspend fun clear()
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY signerName")
    fun signatures(): Flow<List<SignatureEntity>>

    @Upsert
    suspend fun upsert(s: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM signatures")
    suspend fun clear()
}

@Database(
    entities = [SponsorEntity::class, DependentEntity::class, ClaimEntity::class, SignatureEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao
    abstract fun claimDao(): ClaimDao
    abstract fun signatureDao(): SignatureDao

    @Transaction
    open suspend fun replaceAll(
        sponsor: SponsorEntity?,
        dependents: List<DependentEntity>,
        claims: List<ClaimEntity>,
        signatures: List<SignatureEntity>,
    ) {
        familyDao().clearSponsor()
        familyDao().clearDependents()
        claimDao().clear()
        signatureDao().clear()
        sponsor?.let { familyDao().upsertSponsor(it) }
        dependents.forEach { familyDao().upsertDependent(it) }
        claims.forEach { claimDao().upsert(it) }
        signatures.forEach { signatureDao().upsert(it) }
    }

    companion object {
        const val NAME = "easy2642.db"

        /** Version 2 adds saved signatures. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signatures` (`id` TEXT NOT NULL, `signerName` TEXT NOT NULL, " +
                        "`strokesJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
