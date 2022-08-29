# BikeVibes App

Created by Kai Luedemann. Supervised by Professor Mario Nascimento.

## Description

This repository contains the code for BikeVibes, an Android application used to crowdsource data about the 
quality of bicycle pathways. This app tracks accelerometer and location data while biking and sends it to a 
web server to be collected and visualized. More information is available at [bikevibes.ca](https://www.bikevibes.ca).

## Deployment

To work on the project, clone it and use Android Studio. Building the app requires the Google Play Console upload key. 
Once you have that, a new app release can be built as follows:

1. Make sure you have tested the app in release configuration (Build > Select Build Variant)
2. Ensure the build.gradle (app) file contains the correct (incremented) version code
3. Build the signed app bundle as an AAB file (Build > Generate Signed Bundle/APK)
4. Upload the signed bundle to Google Play Console
5. Publish app release in the desired release track

## Acknowledgements
- [NSERC Canada](https://www.nserc-crsng.gc.ca/index_eng.asp) - provided project funding through an Undergraduate Student Research Award (USRA)
- [Cybera](https://www.cybera.ca/) - generously provided computational infrastructure
- [Thunderforest](https://www.thunderforest.com/maps/) - provided map tiles
- [OpenStreetMap](https://www.openstreetmap.org/copyright) - provided underlying map data
- [OSMdroid](https://osmdroid.github.io/osmdroid/index.html) - Android map visualization package
