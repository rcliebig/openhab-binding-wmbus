package de.unidue.stud.sehawagn.openhab.binding.wmbus.internal.discovery;

import static de.unidue.stud.sehawagn.openhab.binding.wmbus.WMBusBindingConstants.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unidue.stud.sehawagn.openhab.binding.wmbus.handler.WMBusBridgeHandler;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.handler.WMBusMessageListener;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.internal.WMBusDevice;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.internal.WMBusHandlerFactory;

public class WMBusDiscoveryService extends AbstractDiscoveryService implements WMBusMessageListener {

    private final Logger logger = LoggerFactory.getLogger(WMBusDiscoveryService.class);

    // add new devices here; these IDs here are generated in getThingTypeUID()
    // basically: control field (0x44 = dec. 68) + manufacturer ID (3 characters) + device version (as output by test
    // program) + device type from jMBus DeviceType class (eg. HEAT_METER = 0x04 = 4)
    // you can get these values using the diagnostics radio message printer included with the JMBus library
    private Map<String, String> typeToWMBUSIdMap;

    private WMBusBridgeHandler bridgeHandler;

    public WMBusDiscoveryService(WMBusBridgeHandler bridgeHandler, Map<String, String> types) {
        super(1);
        this.bridgeHandler = bridgeHandler;
        this.typeToWMBUSIdMap = types;
    }

    public WMBusDiscoveryService(Set<ThingTypeUID> supportedThingTypes, int timeout,
            boolean backgroundDiscoveryEnabledByDefault) throws IllegalArgumentException {
        super(supportedThingTypes, timeout, backgroundDiscoveryEnabledByDefault);
    }

    @Override
    // add new devices there
    public Set<ThingTypeUID> getSupportedThingTypes() {
        logger.trace("getSupportedThingTypes(): currently *implemented* are "
                + WMBusHandlerFactory.SUPPORTED_THING_TYPES_UIDS.toString());
        return WMBusHandlerFactory.SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    protected void startScan() {
        // do nothing since there is no active scan possible at the moment, only receiving
        logger.debug("startScan(): unimplemented - devices will be added upon reception of telegrams from them");
    }

    @Override
    public void onNewWMBusDevice(WMBusDevice wmBusDevice) {
        logger.debug(
                "onnewWMBusDevice(): new device " + wmBusDevice.getOriginalMessage().getSecondaryAddress().toString());
        onWMBusMessageReceivedInternal(wmBusDevice);
    }

    private void onWMBusMessageReceivedInternal(WMBusDevice wmBusDevice) {
        logger.trace("msgreceivedInternal() begin");
        // try to find this device in the list of supported devices
        ThingUID thingUID = getThingUID(wmBusDevice);

        if (thingUID != null) {
            logger.trace("msgReceivedInternal(): device is known");
            // device known -> create discovery result
            ThingUID bridgeUID = bridgeHandler.getThing().getUID();
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(PROPERTY_DEVICE_ID, wmBusDevice.getDeviceId().toString());

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                    .withRepresentationProperty(wmBusDevice.getDeviceId().toString()).withBridge(bridgeUID)
                    .withLabel("WMBus device #" + wmBusDevice.getDeviceId() + " (" + getTypeID(wmBusDevice) + ")")
                    .build();
            logger.trace("msgReceivedInternal(): notifying OpenHAB of new thing");
            thingDiscovered(discoveryResult);
        } else {
            // device unknown -> log message
            logger.debug(
                    "discovered unsupported WMBus device with our type ID {} of WMBus type '{}' with secondary address {}",
                    getTypeID(wmBusDevice), wmBusDevice.getOriginalMessage().getSecondaryAddress().getDeviceType(),
                    wmBusDevice.getOriginalMessage().getSecondaryAddress().toString());
        }
    }

    // checks if this device is of the supported kind -> if yes, will be discovered
    private ThingUID getThingUID(WMBusDevice wmBusDevice) {
        logger.trace("getThingUID begin");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingTypeUID thingTypeUID = getThingTypeUID(wmBusDevice);

        if (thingTypeUID != null && getSupportedThingTypes().contains(thingTypeUID)) {
            logger.trace("getThingUID have bridgeUID " + bridgeUID.toString());
            logger.trace("getThingUID have thingTypeUID " + thingTypeUID.toString());
            return new ThingUID(thingTypeUID, bridgeUID, wmBusDevice.getDeviceId() + "");
        } else {
            logger.debug("get ThingUID found no supported device");
            return null;
        }
    }

    private ThingTypeUID getThingTypeUID(WMBusDevice wmBusDevice) {
        String typeIdString = getTypeID(wmBusDevice);
        logger.trace("getThingTypeUID(): This device has typeID " + typeIdString + " -- supported device types are "
                + Arrays.toString(typeToWMBUSIdMap.keySet().toArray()));
        String thingTypeId = typeToWMBUSIdMap.get(typeIdString);
        return thingTypeId != null ? new ThingTypeUID(BINDING_ID, thingTypeId) : null;
    }

    // this ID is needed to add new devices
    private String getTypeID(WMBusDevice wmBusDevice) {
        return wmBusDevice.getOriginalMessage().getControlField() + ""
                + wmBusDevice.getOriginalMessage().getSecondaryAddress().getManufacturerId() + ""
                + wmBusDevice.getOriginalMessage().getSecondaryAddress().getVersion() + ""
                + wmBusDevice.getOriginalMessage().getSecondaryAddress().getDeviceType().getId();
    }

    public void activate() {
        logger.debug("activate: registering as wmbusmessagelistener");
        bridgeHandler.registerWMBusMessageListener(this);
    }

    @Override
    public void onChangedWMBusDevice(WMBusDevice wmBusDevice) {
        // nothing to do
    }

}
