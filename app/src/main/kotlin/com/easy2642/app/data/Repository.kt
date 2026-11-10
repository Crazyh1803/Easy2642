package com.easy2642.app.data

import com.easy2642.core.ClaimRecord
import com.easy2642.core.Dependent
import com.easy2642.core.FamilyBackup
import com.easy2642.core.Sponsor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class Repository(private val db: AppDatabase) {
    val sponsor: Flow<Sponsor?> = db.familyDao().sponsor().map { it?.toModel() }
    val dependents: Flow<List<Dependent>> = db.familyDao().dependents().map { l -> l.map { it.toModel() } }
    val claims: Flow<List<ClaimRecord>> = db.claimDao().claims().map { l -> l.map { it.toModel() } }

    suspend fun saveSponsor(s: Sponsor) = db.familyDao().upsertSponsor(SponsorEntity.from(s))
    suspend fun saveDependent(d: Dependent) = db.familyDao().upsertDependent(DependentEntity.from(d))
    suspend fun deleteDependent(id: String) = db.familyDao().deleteDependent(id)

    suspend fun claim(id: String): ClaimRecord? = db.claimDao().byId(id)?.toModel()
    suspend fun saveClaim(c: ClaimRecord) = db.claimDao().upsert(ClaimEntity.from(c))
    suspend fun deleteClaim(id: String) = db.claimDao().delete(id)

    suspend fun exportBackup(): String = json.encodeToString(
        FamilyBackup.serializer(),
        FamilyBackup(sponsor = sponsor.first(), dependents = dependents.first(), claims = claims.first()),
    )

    /** Replaces everything with the backup's contents. Throws if the text is not a valid backup. */
    suspend fun importBackup(text: String) {
        val b = json.decodeFromString(FamilyBackup.serializer(), text)
        require(b.formatVersion == 1) { "Unsupported backup version ${b.formatVersion}" }
        db.replaceAll(
            b.sponsor?.let(SponsorEntity::from),
            b.dependents.map(DependentEntity::from),
            b.claims.map(ClaimEntity::from),
        )
    }

    fun close() = db.close()

    companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
