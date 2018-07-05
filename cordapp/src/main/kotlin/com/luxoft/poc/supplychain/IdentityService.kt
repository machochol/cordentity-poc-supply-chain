package com.luxoft.poc.supplychain

import com.luxoft.blockchainlab.corda.hyperledger.indy.flow.AssignPermissionsFlow
import com.luxoft.blockchainlab.corda.hyperledger.indy.flow.CreateClaimDefFlow
import com.luxoft.blockchainlab.corda.hyperledger.indy.flow.CreateSchemaFlow
import com.luxoft.blockchainlab.corda.hyperledger.indy.flow.IssueClaimFlow
import com.luxoft.blockchainlab.hyperledger.indy.IndyUser
import com.luxoft.poc.supplychain.data.schema.DiagnosisDetails
import com.luxoft.poc.supplychain.data.schema.PackageReceipt
import com.luxoft.poc.supplychain.data.schema.PersonalInformation
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import java.time.Duration


val agentCert = CordaX500Name("SovrinAgent", "London", "GB")

val issuerCert = CordaX500Name("Manufacture", "London", "GB")
val issuerDid = "CzSfMVfq7U5pjTVtzd5uop"

val treatmentCert = CordaX500Name("TreatmentCenter", "London", "GB")
val treatmentDid = "V4SGRU86Z58d6TV7PBUe6f"

val diagnosisDetailsProposal = DiagnosisDetails()
        .addAttr(DiagnosisDetails.Attributes.Stage, "4")
        .addAttr(DiagnosisDetails.Attributes.Disease, "leukemia")
        .addAttr(DiagnosisDetails.Attributes.MedicineName, "package-name")
        .addAttr(DiagnosisDetails.Attributes.Recommendation, "package-required")
        .build()

val personalInformationProposal = PersonalInformation()
        .addAttr(PersonalInformation.Attributes.Age, "20")
        .addAttr(PersonalInformation.Attributes.Nationality, "eu")
        .addAttr(PersonalInformation.Attributes.Forename, "Mike J")
        .build()

class IdentityService(private val rpc: CordaRPCOps, private val timeout: Duration = Duration.ofSeconds(30)) {
    fun initTreatmentIndy() {
        val packageSchemaName = PackageReceipt.schemaName
        val packageSchemaVersion = PackageReceipt.schemaVersion
        val packageSchemaAttrs = PackageReceipt().getSchemaAttrs().map { it.name }

        initIndySchema(treatmentDid, packageSchemaName, packageSchemaVersion, packageSchemaAttrs)

        // Collecting claim from insurance
        val diagnosisSchemaName = DiagnosisDetails.schemaName
        val diagnosisSchemaVersion = DiagnosisDetails.schemaVersion
        val diagnosisSchemaAttrs = DiagnosisDetails().getSchemaAttrs().map { it.name }

        initIndySchema(treatmentDid, diagnosisSchemaName, diagnosisSchemaVersion, diagnosisSchemaAttrs)
        issueClaimTo(treatmentDid, agentCert, diagnosisDetailsProposal, diagnosisSchemaName, diagnosisSchemaVersion)
    }

    fun initIssuerIndy() {
        rpc.startFlow(
                AssignPermissionsFlow::Issuer,
                null, null, treatmentCert
        )

        // Collecting claim from government
        val privateInfoSchemaName = PersonalInformation.schemaName
        val privateInfoSchemaVersion = PersonalInformation.schemaVersion
        val privateInfoSchemaAttrs = PersonalInformation().getSchemaAttrs().map { it.name }

        initIndySchema(issuerDid, privateInfoSchemaName, privateInfoSchemaVersion, privateInfoSchemaAttrs)
        issueClaimTo(issuerDid, agentCert, personalInformationProposal, privateInfoSchemaName, privateInfoSchemaVersion)
    }

    fun initIndySchema(myDid: String, schemaName: String, schemaVersion: String, schemaAttrs: List<String>) {
        val schemaResFuture = rpc.startFlow(
                CreateSchemaFlow::Authority,
                schemaName, schemaVersion, schemaAttrs
        ).returnValue

        val claimDefFuture = rpc.startFlow(
                CreateClaimDefFlow::Authority,
                myDid, schemaName, schemaVersion
        ).returnValue

        schemaResFuture.getOrThrow(timeout)
        claimDefFuture.getOrThrow(timeout)
    }

    fun issueClaimTo(
            myDid: String,
            toName: CordaX500Name,
            claim: String,
            schemaName: String,
            schemaVersion: String) {

        val schema = IndyUser.SchemaDetails(schemaName, schemaVersion, myDid)

        val claimIssueFuture = rpc.startFlow(
                IssueClaimFlow::Issuer,
                "xxx",
                schema, claim, toName
        ).returnValue

        claimIssueFuture.getOrThrow(timeout)
    }
}