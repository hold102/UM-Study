package my.edu.um.study.announcement;

import java.util.List;

public record Announcement(
        String id,
        String title,
        String body,
        String postedAt,
        String author,
        Integer week,
        List<String> images,
        List<AnnouncementLink> links
) {}
