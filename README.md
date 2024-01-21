# Transport Monitoring Project - IoT - Giovanetti, La Corte, Scarrà

## Abstract
The goal of this project is to establish an infrastructure for monitoring the quality of road trips, primarily involving buses and trains. The focus areas include:

- Temperature
- Crowding
- Noise Pollution
- Vehicle Movements
- Punctuality

### Technologies
- Android
  - We've developed a custom Android app in Java to collect data from the phone's various sensors.
  - Temperature and crowd data are simulated since phones lack sensors for these metrics.
  - The app utilizes the Eclipse Paho Java library, employing the MQTT protocol for connection and message transmission.

- NodeRED
  - The workflow achieves several objectives:
    - Simulates crowding and temperature sensors.
    - Acts as an intermediary between the application, using an MQTT broker node powered by aedes.js, and a ThingWorx node responsible for communicating with ThingWorx via the HTTP REST protocol.
    - Manages a Telegram bot to send alerts when needed.
    - Displays real-time location on a map.

- ThingWorx
  - Multiple entities collaborate to visualize data effectively:
    - Thing: Represents a single bus.
    - Thing Value Stream: Represents the bus associated with the thing, derived from data of the value stream.
    - Alerts: Triggered when Thing’s properties satisfy specific conditions.
    - Application Key: Necessary to pass data from NodeRED to Thingworx.
    - Mashups: Dashboards showing data in tables, line charts, or gauges.

### Dashboards

#### Map - NodeRED
- Pointer Movement: Links to dashboards, icon of the moving vehicle.
- Line Representing Vehicle Movements: Links to ThingWorx Mashups.

#### Mashups - ThingWorx
- Accelerometers: Displays gauges and charts of Accelerometers X, Y, and Z to monitor the current value and its evolution over time.
- Noise/Temperature/Crowding: Similar structure to the Accelerometers mashup but focuses on Noise, Crowding, and Temperature metrics.
- Alerts: Displays the last alert triggered for each parameter, listing relevant information.

### Project Report
[Project Report](https://lorenzolacorte.github.io/iot-project-report/)
