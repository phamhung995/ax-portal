package com.furelise.skillmanagement.home.dto;

import java.util.List;
import java.util.Map;

/**
 * Selector data for the Home Chart tab (Home_Chart_SRS §6.1):
 * distinct groups + parts per group (org rows employed='Y').
 */
public record ChartDimensions(
        List<String> groups,
        Map<String, List<String>> partsByGroup) {
}
