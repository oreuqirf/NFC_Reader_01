package com.example.nfc_reader_01.collection

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: InstrumentRecord)

    @Query("SELECT * FROM batch_collection_table ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<InstrumentRecord>

    // --- ESTAS SON LAS 3 FUNCIONES QUE EL VIEWMODEL ESTABA BUSCANDO ---

    @Delete
    suspend fun deleteRecord(record: InstrumentRecord)

    @Query("DELETE FROM batch_collection_table")
    suspend fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM batch_collection_table")
    suspend fun getRecordCount(): Int
}