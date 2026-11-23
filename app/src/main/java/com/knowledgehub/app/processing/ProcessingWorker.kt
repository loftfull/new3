package com.knowledgehub.app.processing

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.knowledgehub.core.CaptureRepository
import com.knowledgehub.core.processing.LocalMaterialAnalyzer
import com.knowledgehub.core.processing.MaterialAnalyzer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class KnowledgeHubApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration by lazy {
        Configuration.Builder()
            .setWorkerFactory(ProcessingWorker.Factory(LocalMaterialAnalyzer))
            .build()
    }
}

internal class ProcessingWorker(
    context: Context,
    parameters: WorkerParameters,
    private val analyzer: MaterialAnalyzer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        CaptureRepository.shared(applicationContext).processPending(analyzer)
        Result.success()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    class Factory(private val analyzer: MaterialAnalyzer) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ) = if (workerClassName == ProcessingWorker::class.java.name) {
            ProcessingWorker(appContext, workerParameters, analyzer)
        } else null
    }
}

internal fun enqueueProcessing(context: Context) {
    val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "knowledge-hub-processing",
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        request,
    )
}
