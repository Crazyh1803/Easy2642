package com.easy2642.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
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

@Database(
    entities = [SponsorEntity::class, DependentEntity::class, ClaimEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao
    abstract fun claimDao(): ClaimDao

    @Transaction
    open suspend fun replaceAll(sponsor: SponsorEntity?, dependents: List<DependentEntity>, claims: List<ClaimEntity>) {
        familyDao().clearSponsor()
        familyDao().clearDependents()
        claimDao().clear()
        sponsor?.let { familyDao().upsertSponsor(it) }
        dependents.forEach { familyDao().upsertDependent(it) }
        claims.forEach { claimDao().upsert(it) }
    }

    companion object {
        const val NAME = "easy2642.db"
    }
}
