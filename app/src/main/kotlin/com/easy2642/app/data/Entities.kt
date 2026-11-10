package com.easy2642.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.easy2642.core.ClaimRecord
import com.easy2642.core.Currency
import com.easy2642.core.Dependent
import com.easy2642.core.PersonName
import com.easy2642.core.Relationship
import com.easy2642.core.SavedSignature
import com.easy2642.core.Sex
import com.easy2642.core.Signature
import com.easy2642.core.Sponsor
import kotlinx.serialization.json.Json

@Entity(tableName = "sponsor")
data class SponsorEntity(
    @PrimaryKey val id: Int = 1,
    val lastName: String,
    val firstName: String,
    val middleInitial: String,
    val dodIdOrSsn: String,
    val branchOfService: String,
    val dutyStatus: String,
    val streetAddress: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val phoneNumber: String,
    val dob: String,
    val sex: String?,
) {
    fun toModel() = Sponsor(
        PersonName(lastName, firstName, middleInitial), dodIdOrSsn, branchOfService, dutyStatus,
        streetAddress, city, state, zipCode, phoneNumber, dob, sex?.let(Sex::valueOf),
    )

    companion object {
        fun from(s: Sponsor) = SponsorEntity(
            1, s.name.lastName, s.name.firstName, s.name.middleInitial, s.dodIdOrSsn, s.branchOfService,
            s.dutyStatus, s.streetAddress, s.city, s.state, s.zipCode, s.phoneNumber, s.dob, s.sex?.name,
        )
    }
}

@Entity(tableName = "dependents")
data class DependentEntity(
    @PrimaryKey val id: String,
    val relationship: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String,
    val dob: String,
    val sex: String?,
    val dodId: String,
) {
    fun toModel() = Dependent(
        id, Relationship.valueOf(relationship), PersonName(lastName, firstName, middleInitial),
        dob, sex?.let(Sex::valueOf), dodId,
    )

    companion object {
        fun from(d: Dependent) = DependentEntity(
            d.id, d.relationship.name, d.name.lastName, d.name.firstName, d.name.middleInitial,
            d.dob, d.sex?.name, d.dodId,
        )
    }
}

@Entity(tableName = "claims")
data class ClaimEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val patientName: String,
    val provider: String,
    val dateOfService: String,
    val amountMinor: Long,
    val currency: String,
    val dossierUri: String,
    val isPacketGenerated: Boolean,
    val isFiled: Boolean,
    val filedDate: String?,
    val isReimbursed: Boolean,
    val reimbursedDate: String?,
    val notes: String,
) {
    fun toModel() = ClaimRecord(
        id, patientId, patientName, provider, dateOfService, amountMinor, Currency.valueOf(currency),
        dossierUri, isPacketGenerated, isFiled, filedDate, isReimbursed, reimbursedDate, notes,
    )

    companion object {
        fun from(c: ClaimRecord) = ClaimEntity(
            c.id, c.patientId, c.patientName, c.provider, c.dateOfService, c.amountMinor, c.currency.name,
            c.dossierUri, c.isPacketGenerated, c.isFiled, c.filedDate, c.isReimbursed, c.reimbursedDate, c.notes,
        )
    }
}

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val signerName: String,
    val strokesJson: String,
) {
    fun toModel() = SavedSignature(id, signerName, Json.decodeFromString(Signature.serializer(), strokesJson))

    companion object {
        fun from(s: SavedSignature) =
            SignatureEntity(s.id, s.signerName, Json.encodeToString(Signature.serializer(), s.signature))
    }
}
