package com.intellisoft.chanjoke.fhir.data

import android.content.Context
import androidx.work.WorkerParameters
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.FhirSyncWorker

class FhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    FhirSyncWorker(appContext, workerParams) {

    override fun getDownloadWorkManager(): DownloadWorkManager {
        return TimestampBasedDownloadWorkManagerImpl(
            FhirApplication.dataStore(applicationContext))
    }

    override fun getConflictResolver() = AcceptLocalConflictResolver

    override fun getFhirEngine() = FhirApplication.fhirEngine(applicationContext)

//    override fun getUploadStrategy(): UploadStrategy = UploadStrategy.AllChangesSquashedBundlePut
}