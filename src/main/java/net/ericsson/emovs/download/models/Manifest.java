package net.ericsson.emovs.download.models;

import net.ericsson.emovs.download.models.adaptation.AdaptationSet;

import java.util.ArrayList;

/**
 * Created by Joao Coelho on 2017-11-28.
 */

public class Manifest {
    public double durationSeconds;
    public ArrayList<AdaptationSet> adaptationSets;

    public Manifest() {
        this.adaptationSets = new ArrayList<>();
    }
}
