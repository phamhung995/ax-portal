package com.skillmanagement.desktop.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.desktop.model.DesktopPackage;
import com.skillmanagement.desktop.model.DesktopRelease;
import com.skillmanagement.desktop.service.DesktopFailure;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@Profile({"desktop-pilot","desktop-test"})
@Transactional(readOnly=true)
public class DesktopReleaseRepository {
    public record StoredRelease(DesktopRelease metadata,DesktopPackage artifact,boolean withdrawn) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final String SELECT="SELECT r.id AS release_id,r.skill_id,r.sequence,CASE WHEN octet_length(r.metadata_json)<=65536 THEN r.metadata_json ELSE NULL END AS metadata_json,r.withdrawn,p.id AS package_id,p.format,p.file_name,p.size_bytes,p.sha256,p.storage_key FROM desktop_release r JOIN desktop_package p ON p.id=r.package_id ";
    public DesktopReleaseRepository(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }
    public Optional<StoredRelease> find(long skillId,String releaseId) {
        if(!releaseId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))return Optional.empty();
        return jdbc.query(SELECT+"WHERE r.skill_id=? AND r.id=?",(row,index)->read(row),skillId,UUID.fromString(releaseId)).stream().findFirst();
    }
    public Optional<StoredRelease> latest(long skillId) {
        return jdbc.query(SELECT+"WHERE r.skill_id=? AND r.withdrawn=false ORDER BY r.sequence DESC FETCH FIRST 1 ROW ONLY",(row,index)->read(row),skillId).stream().findFirst();
    }
    private StoredRelease read(ResultSet row) throws SQLException {
        DesktopRelease metadata;
        try { metadata=mapper.readValue(row.getString("metadata_json"),DesktopRelease.class); }
        catch(Exception invalid) { throw unavailable(); }
        if(metadata==null || !row.getString("release_id").equals(metadata.id()) || !Long.toString(row.getLong("skill_id")).equals(metadata.skillId()) || row.getLong("sequence")!=metadata.sequence() || metadata.version()==null || metadata.version().isBlank() || metadata.installName()==null || !metadata.installName().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || metadata.publishedAt()==null || metadata.changelog()==null)throw unavailable();
        String format=row.getString("format"),name=row.getString("file_name"),sha=row.getString("sha256");
        long size=row.getLong("size_bytes");
        if(!List.of("zip","7z").contains(format) || name==null || name.isBlank() || name.length()>255 || name.chars().anyMatch(c->Character.isISOControl(c) || "\\/:*?\"<>|".indexOf(c)>=0) || sha==null || !sha.matches("[a-fA-F0-9]{64}") || size<1 || size>104857600)throw unavailable();
        var artifact=new DesktopPackage(UUID.fromString(row.getString("package_id")),format,name,size,sha,UUID.fromString(row.getString("storage_key")));
        return new StoredRelease(metadata,artifact,row.getBoolean("withdrawn"));
    }
    private DesktopFailure unavailable() { return new DesktopFailure(503,"RELEASE_UNAVAILABLE","The published desktop release cannot be verified."); }
}
