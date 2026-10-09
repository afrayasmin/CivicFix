package com.example.civicfix.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.civicfix.model.Report;

import java.util.List;

@Dao
public interface ReportDao {

    @Insert
    long insert(Report report);

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    LiveData<List<Report>> getAllLive();

    @Query("SELECT * FROM reports WHERE syncState = 'PENDING_SYNC'")
    List<Report> getPendingReports();

    @Query("SELECT COUNT(*) FROM reports WHERE syncState = 'PENDING_SYNC'")
    LiveData<Integer> getPendingCountLive();

    @Query("UPDATE reports SET address = :address WHERE id = :id")
    void updateAddress(long id, String address);

    @Query("UPDATE reports SET syncState = 'SYNCED', cloudId = :cloudId, photoUrl = :photoUrl WHERE id = :id")
    void markSynced(long id, String cloudId, String photoUrl);
}