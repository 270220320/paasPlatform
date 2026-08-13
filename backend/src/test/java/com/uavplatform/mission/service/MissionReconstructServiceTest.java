package com.uavplatform.mission.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionReconstructServiceTest {

    @Test
    void folderCandidates_includesLeafAndTaskFolder() {
        var c = MissionReconstructService.folderCandidatesFromFilePath("83/84/85");
        assertEquals(85L, c.primaryId());
        assertEquals(List.of(85, 84), c.folderIds());
    }

    @Test
    void folderIdFromFilePath_usesLeafWherePhotosLive() {
        assertEquals(85L, MissionReconstructService.folderIdFromFilePath("83/84/85"));
        assertEquals(66L, MissionReconstructService.folderIdFromFilePath("60/65/66"));
    }

    @Test
    void folderIdFromFilePath_shortPath() {
        assertEquals(12L, MissionReconstructService.folderIdFromFilePath("12"));
        assertEquals(10L, MissionReconstructService.folderIdFromFilePath("9/10"));
    }

    @Test
    void folderIdFromFilePath_invalid() {
        assertNull(MissionReconstructService.folderIdFromFilePath(null));
        assertNull(MissionReconstructService.folderIdFromFilePath(""));
        assertNull(MissionReconstructService.folderIdFromFilePath("abc/def"));
    }

    @Test
    void majorityFolderCandidates_picksMostCommonPath() {
        var c = MissionReconstructService.majorityFolderCandidates(List.of(
                "83/84/85", "83/84/85", "83/84/85", "60/65/66"));
        assertEquals(85L, c.primaryId());
        assertTrue(c.folderIds().contains(85));
        assertTrue(c.folderIds().contains(84));
    }
}
