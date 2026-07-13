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
    
    @Query("DELETE FROM members WHERE name = :name")
    void deleteAllMembersByName(String name);

    @Query("SELECT * FROM members WHERE name = :name ORDER BY id DESC")
    List<MemberEntity> getMembersByNameDesc(String name);
    
    @Query("SELECT * FROM members WHERE name = :name ORDER BY id ASC")
    List<MemberEntity> getMembersByNameAsc(String name);
    
    @Query("DELETE FROM members WHERE id = :id")
    void deleteMemberById(int id);
    
    @Query("SELECT COUNT(*) FROM members")
    int getMemberCount();

    @Query("SELECT COUNT(DISTINCT name) FROM members")
    int getUniqueMemberCount();

    @Query("DELETE FROM members WHERE name = :name AND id NOT IN (SELECT id FROM members WHERE name = :name ORDER BY id DESC LIMIT :limit)")
    void keepRecentFaces(String name, int limit);
}
