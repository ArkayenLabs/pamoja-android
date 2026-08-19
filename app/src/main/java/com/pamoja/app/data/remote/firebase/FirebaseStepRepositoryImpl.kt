package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.remote.model.StepEntryDto
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.StepRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class FirebaseStepRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val healthConnectReader: HealthConnectReader
) : StepRepository {

    private val stepsCollection = firestore.collection("steps")

    override suspend fun saveStepEntry(stepEntry: StepEntry): Result<Unit> {
        return try {
            val dto = StepEntryDto.fromDomain(stepEntry)
            stepsCollection
                .document("${stepEntry.userId}_${stepEntry.date}")
                .set(dto)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun getStepsForUser(userId: String, date: String): Result<StepEntry> {
        return try {
            val snapshot = stepsCollection
                .document("${userId}_${date}")
                .get()
                .await()
            val dto = snapshot.toObject(StepEntryDto::class.java)
                ?: return Result.success(StepEntry(userId = userId, stepCount = 0L, date = date))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun getStepsForUserInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Flow<List<StepEntry>> = callbackFlow {
        val listener = stepsCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull {
                    it.toObject(StepEntryDto::class.java)?.toDomain()
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun syncTodaySteps(userId: String): Result<Unit> {
        return try {
            val todaySteps = healthConnectReader.readTodaySteps()
                ?: return Result.success(Unit) // HC unavailable or permission not granted
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val entry = StepEntry(
                userId    = userId,
                stepCount = todaySteps,
                date      = today
            )
            saveStepEntry(entry)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }
}