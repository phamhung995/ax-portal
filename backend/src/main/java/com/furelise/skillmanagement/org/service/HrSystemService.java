package com.furelise.skillmanagement.org.service;

import com.furelise.skillmanagement.org.dto.HrImportResponse;
import com.furelise.skillmanagement.org.dto.HrSearchRequest;
import com.furelise.skillmanagement.org.dto.HrSearchResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Human Resource System List master data (person -> system assignments with
 * monthly input). Import is a full replace, like the Systems import.
 */
public interface HrSystemService {

    HrSearchResult search(HrSearchRequest request);

    List<String> partOptions();

    HrImportResponse importExcel(MultipartFile file, String adminLoginId);
}
