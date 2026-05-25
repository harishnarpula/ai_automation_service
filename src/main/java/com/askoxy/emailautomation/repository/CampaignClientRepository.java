package com.askoxy.emailautomation.repository;

import com.askoxy.emailautomation.entity.CampaignClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignClientRepository extends JpaRepository<CampaignClient, Long> {

    /** All clients for a given campaign (used at trigger time) */
    List<CampaignClient> findByCampaignId(String campaignId);

    /** Only PENDING clients — used when sending after approval */
    List<CampaignClient> findByCampaignIdAndStatus(String campaignId, String status);

    /** Count by status for summary reporting */
    long countByCampaignIdAndStatus(String campaignId, String status);

    /** How many clients total in a campaign */
    long countByCampaignId(String campaignId);

    /** Sender distribution for a campaign — useful for the upload response */
    @Query("SELECT c.assignedSender, COUNT(c) FROM CampaignClient c " +
            "WHERE c.campaignId = :campaignId GROUP BY c.assignedSender")
    List<Object[]> countGroupedBySender(@Param("campaignId") String campaignId);
}