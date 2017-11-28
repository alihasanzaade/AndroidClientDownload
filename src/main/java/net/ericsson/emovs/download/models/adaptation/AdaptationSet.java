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
    public long duration;
    public long startNumber;
    public ArrayList<Track> tracks;

    public AdaptationSet() {
        this.tracks = new ArrayList<>();
    }

    public AdaptationSet(AdaptationSet other) {
        this.tracks = other.tracks;
        this.id = other.id;
        this.mimeType = other.mimeType;
        this.licenseServerUrl = other.licenseServerUrl;
        this.initData = other.initData;
        this.segmentTemplate = other.segmentTemplate;
        this.initSegment = other.initSegment;
        this.timescale = other.timescale;
        this.duration = other.duration;
        this.startNumber = other.startNumber;
    }
}
