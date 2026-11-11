package com.sicost.contacts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CandidateContactDtos {
    private CandidateContactDtos() {}
    public record Contact(int sn,String status,String memo,LocalDate contactDate,String actorId,String actorName) {}
    public record Page(List<Contact> items,long total,int page,int size) {}
    public record Created(Contact contact,String contactStatus,LocalDate lastContactDate) {}
    public record Create(String contactStatus,String memo,String expectedRevision) {}
    public record Query(int page,int size) {}
    public record Selection(String id,int size,long offset) {}
    public record Candidate(String id,String status,LocalDate lastContactDate) {}
    public record Mutation(String id,int sn,String status,String memo,LocalDate contactDate,String actor,LocalDateTime now) {}
}
