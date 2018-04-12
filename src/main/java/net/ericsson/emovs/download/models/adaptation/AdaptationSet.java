package net.ericsson.emovs.download.models.adaptation;

import net.ericsson.emovs.download.models.tracks.Track;

import java.util.ArrayList;

/**
 * Created by Joao Coelho on 2017-11-28.
 */

public class AdaptationSet {
    public String id;
    public String mimeType;
    public String licenseServerUrl;
    public String initData;
    public String segmentTemplate;
    public String initSegment;
    public long timescale;
    public long segmentDuration;
    public long startNumber;
    public long increment;
    public ArrayList<Track> tracks;
    public double segmentDurationSeconds;
    public long segmentCount;
    public String lang;

    public AdaptationSet() {
        this.tracks = new ArrayList<>();
    }

    public AdaptationSet(AdaptationSet other) {
        this.id = other.id;
        this.mimeType = other.mimeType;
        this.licenseServerUrl = other.licenseServerUrl;
        this.initData = other.initData;
        this.segmentTemplate = other.segmentTemplate;
        this.initSegment = other.initSegment;
        this.timescale = other.timescale;
        this.segmentDuration = other.segmentDuration;
        this.startNumber = other.startNumber;
        this.tracks = other.tracks;
        this.segmentDurationSeconds = other.segmentDurationSeconds;
        this.segmentCount = other.segmentCount;
        this.lang = other.lang;
        this.increment = other.increment;
    }
}
