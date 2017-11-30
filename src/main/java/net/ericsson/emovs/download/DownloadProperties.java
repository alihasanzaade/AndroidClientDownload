package net.ericsson.emovs.download;

import java.util.ArrayList;

/**
 * Created by Joao Coelho on 2017-11-30.
 */

public class DownloadProperties {
    ArrayList<String> selectedAudioLanguages;
    ArrayList<String> selectedTextLanguages;
    int minBitrate;
    int maxBitrate;

    public DownloadProperties() {
        maxBitrate = Integer.MAX_VALUE;
        minBitrate = Integer.MIN_VALUE;

    }

    public ArrayList<String> getSelectedAudioLanguages() {
        return selectedAudioLanguages;
    }

    public DownloadProperties withSelectedAudioLanguages(ArrayList<String> selectedAudioLanguages) {
        this.selectedAudioLanguages = selectedAudioLanguages;
        return this;
    }

    public boolean hasAudioLanguage(String qLang) {
        if(selectedAudioLanguages == null || qLang == null) {
            return true;
        }
        for (String lang : selectedAudioLanguages) {
            if(lang.equals(qLang)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<String> getSelectedTextLanguages() {
        return selectedTextLanguages;
    }

    public DownloadProperties withSelectedTextLanguages(ArrayList<String> selectedTextLanguages) {
        this.selectedTextLanguages = selectedTextLanguages;
        return this;
    }

    public boolean hasTextLanguage(String qLang) {
        if(selectedTextLanguages == null || qLang == null) {
            return true;
        }
        for (String lang : selectedTextLanguages) {
            if(lang.equals(qLang)) {
                return true;
            }
        }
        return false;
    }

    public int getMinBitrate() {
        return minBitrate;
    }

    public DownloadProperties withMinBitrate(int minBitrate) {
        this.minBitrate = minBitrate;
        return this;
    }

    public int getMaxBitrate() {
        return maxBitrate;
    }

    public DownloadProperties withMaxBitrate(int maxBitrate) {
        this.maxBitrate = maxBitrate;
        return this;
    }
}
