package com.example.facerecognitionimages.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LogDao {
    @Query("SELECT * FROM logs ORDER BY date DESC, time DESC")
    List<LogEntity> getAllLogs();

    @Query("SELECT * FROM logs WHERE date = :date ORDER BY time DESC")
    List<LogEntity> getLogsByDate(String date);

    @Insert
    void insertLog(LogEntity log);

    @Query("SELECT COUNT(*) FROM logs WHERE date = :date")
    int getLogCountByDate(String date);
    
    @Query("SELECT * FROM logs WHERE name = :name AND date = :date ORDER BY time ASC")
    List<LogEntity> getLogsForUserOnDate(String name, String date);
    
    @Query("DELETE FROM logs")
    void deleteAllLogs();
}
