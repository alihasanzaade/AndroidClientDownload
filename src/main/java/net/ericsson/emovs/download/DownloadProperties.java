package net.ericsson.emovs.download;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Joao Coelho on 2017-11-30.
 */

public class DownloadProperties implements Serializable {
    public static final DownloadProperties DEFAULT = new DownloadProperties();

    ArrayList<String> selectedAudioLanguages;
    ArrayList<String> selectedTextLanguages;
    int minAudioBitrate;
    int maxAudioBitrate;
    int minVideoBitrate;
    int maxVideoBitrate;

    public DownloadProperties() {
        maxAudioBitrate = Integer.MAX_VALUE;
        minAudioBitrate = Integer.MIN_VALUE;
        maxVideoBitrate = Integer.MAX_VALUE;
        minVideoBitrate = Integer.MIN_VALUE;
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

    public int getMinAudioBitrate() {
        return minAudioBitrate;
    }

    public DownloadProperties withMinAudioBitrate(int minBitrate) {
        this.minAudioBitrate = minBitrate;
        return this;
    }

    public int getMaxAudioBitrate() {
        return maxAudioBitrate;
    }

    public DownloadProperties withMaxVideoBitrate(int maxBitrate) {
        this.maxVideoBitrate = maxBitrate;
        return this;
    }

    public int getMinVideoBitrate() {
        return minVideoBitrate;
    }

    public DownloadProperties withMinVideoBitrate(int minBitrate) {
        this.minVideoBitrate = minBitrate;
        return this;
    }

    public int getMaxVideoBitrate() {
        return maxVideoBitrate;
    }

    public DownloadProperties withMaxAudioBitrate(int maxBitrate) {
        this.maxVideoBitrate = maxBitrate;
        return this;
    }


    public JSONObject getJson() {
        JSONObject props = new JSONObject();
        try {
            props.put("minAudioBitrate", minAudioBitrate);
            props.put("maxAudioBitrate", maxAudioBitrate);
            props.put("minVideoBitrate", minVideoBitrate);
            props.put("maxVideoBitrate", maxVideoBitrate);

            if (selectedAudioLanguages != null) {
                props.put("selectedAudioLanguages", new JSONArray(selectedAudioLanguages));
            }

            if (selectedTextLanguages != null) {
                props.put("selectedTextLanguages",  new JSONArray(selectedTextLanguages));
            }
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return props;
    }

    public void fromJson(JSONObject props) {
        try {
            minAudioBitrate = props.optInt("minAudioBitrate");
            maxAudioBitrate = props.optInt("maxAudioBitrate");
            minVideoBitrate = props.optInt("minVideoBitrate");
            maxVideoBitrate = props.optInt("maxVideoBitrate");

            if (props.has("selectedAudioLanguages")) {
                selectedAudioLanguages = new ArrayList<>();
                JSONArray selectedAudioLanguagesJson = props.optJSONArray("selectedAudioLanguages");
                for (int i = 0; i < selectedAudioLanguagesJson.length(); ++i) {
                    selectedAudioLanguages.add(selectedAudioLanguagesJson.getString(i));
                }
            }

            if (props.has("selectedTextLanguages")) {
                selectedTextLanguages = new ArrayList<>();
                JSONArray selectedTextLanguagesJson = props.optJSONArray("selectedTextLanguages");
                for (int i = 0; i < selectedTextLanguagesJson.length(); ++i) {
                    selectedTextLanguages.add(selectedTextLanguagesJson.getString(i));
                }
            }
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
