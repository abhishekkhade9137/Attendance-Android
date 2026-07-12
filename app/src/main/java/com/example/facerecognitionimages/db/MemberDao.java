package com.example.facerecognitionimages.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemberDao {
    @Query("SELECT * FROM members")
    List<MemberEntity> getAllMembers();

    @Insert
    void insertMember(MemberEntity member);

    @Delete
    void deleteMember(MemberEntity member);
    
    @Query("SELECT COUNT(*) FROM members")
    int getMemberCount();
}
