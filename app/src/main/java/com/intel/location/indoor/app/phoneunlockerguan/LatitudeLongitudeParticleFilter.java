package com.intel.location.indoor.app.phoneunlockerguan;

import android.location.Location;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ParticleFilter class for particle filtering. Adapted from https://github.com/erhs-53-hackers/Particle-Filter/blob/master/src/particlefilter/Particle.java.
 *
 * Edited by: Grace Guan
 * Last Edited: August 2016
 */
public class LatitudeLongitudeParticleFilter {

    LatitudeLongitudeParticle[] particles;
    int numParticles = 0;
    Random gen = new Random();

    public LatitudeLongitudeParticleFilter(int numParticles, Location[] landmarks, int width, int height) {
        this.numParticles = numParticles;

        particles = new LatitudeLongitudeParticle[numParticles];
        for (int i = 0; i < numParticles; i++) {
            particles[i] = new LatitudeLongitudeParticle(landmarks, width, height);
        }
    }

    public void setNoise(float Fnoise, float Tnoise, float Snoise) {
        for (int i = 0; i < numParticles; i++) {
            particles[i].setNoise(Fnoise, Tnoise, Snoise);
        }
    }

    public void move(float turn, float forward) throws Exception {
        for (int i = 0; i < numParticles; i++) {
            particles[i].move(turn, forward);
        }
    }

    public void resample(float[] measurement) throws Exception {
        LatitudeLongitudeParticle[] new_particles
                = new LatitudeLongitudeParticle[numParticles];

        for (int i = 0; i < numParticles; i++) {
            particles[i].measurementProb(measurement);
        }
        float B = 0f;
        LatitudeLongitudeParticle best = getBestParticle();
        int index = (int) gen.nextFloat() * numParticles;
        for (int i = 0; i < numParticles; i++) {
            B += gen.nextFloat() * 2f * best.probability;
            while (B > particles[index].probability) {
                B -= particles[index].probability;
                index = circle(index + 1, numParticles);
            }
            new_particles[i] = new LatitudeLongitudeParticle(particles[index].landmarks, particles[index].worldWidth, particles[index].worldHeight);
            new_particles[i].set(particles[index].x, particles[index].y, particles[index].orientation, particles[index].probability);
            new_particles[i].setNoise(particles[index].forwardNoise, particles[index].turnNoise, particles[index].senseNoise);
        }

        particles = new_particles;
    }

    private int circle(int num, int length) {
        while (num > length - 1) {
            num -= length;
        }
        while (num < 0) {
            num += length;
        }
        return num;
    }

    public LatitudeLongitudeParticle getBestParticle() {
        LatitudeLongitudeParticle particle = particles[0];
        for (int i = 0; i < numParticles; i++) {
            if (particles[i].probability > particle.probability) {
                particle = particles[i];
            }
        }
        return particle;
    }

    public LatitudeLongitudeParticle getAverageParticle() {
        LatitudeLongitudeParticle p = new LatitudeLongitudeParticle(particles[0].landmarks, particles[0].worldWidth, particles[0].worldHeight);
        float x = 0, y = 0, orient = 0, prob = 0;
        for(int i=0;i<numParticles;i++) {
            x += particles[i].x;
            y += particles[i].y;
            orient += particles[i].orientation;
            prob += particles[i].probability;
        }
        x /= numParticles;
        y /= numParticles;
        orient /= numParticles;
        prob /= numParticles;
        try {
            p.set(x, y, orient, prob);
        } catch (Exception ex) {
            Logger.getLogger(LatitudeLongitudeParticleFilter.class.getName()).log(Level.SEVERE, null, ex);
        }

        p.setNoise(particles[0].forwardNoise, particles[0].turnNoise, particles[0].senseNoise);

        return p;
    }

    @Override
    public String toString() {
        String res = "";
        for (int i = 0; i < numParticles; i++) {
            res += particles[i].toString() + "\n";
        }
        return res;
    }

}
