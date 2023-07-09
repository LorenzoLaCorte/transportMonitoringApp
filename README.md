# Transport Monitoring Project - IoT - Giovanetti, La Corte, Scarrà

## From Android to NodeRED
...

## From NodeRED to Thingworx 
From the palette of NodeRED we downloaded the library ```node-red-contrib-thingrest```.
Within this library we can finde the node ```REST Thing node```, which will be later configured.

Then we created a generic thing in ThingWorx with property accelerometerX: long.

Then we generated an Application Key (Composer -> New -> Application Key), setting as ```User Name Reference``` our username.

We then configured the ```REST Thing node``` inserting:
- url of thingworx (http://212.78.1.205:8080),
- thing name,
- application key.
