package com.clarecontrols.equator.solstice.api.beta;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.clarecontrols.equator.solstice.db.entities.DeviceClass;
import com.clarecontrols.equator.solstice.db.entities.DeviceItem;
import com.clarecontrols.equator.solstice.db.entities.DeviceType;

/**
 * JDeviceItem represents export/import DeviceItem helper.
 */
final class JDeviceItem {

    /**
     * @param devices devices to export
     * @param jsonObject json to export to
     * @throws JSONException json exception
     */
    static void exportDeviceItems(final Set<DeviceItem> devices, final JSONObject jsonObject) throws JSONException {
        Objects.requireNonNull(devices);
        Objects.requireNonNull(jsonObject);

        // keys [ devices, device_device ]
        JUtils.getArray(JKey.DEVICES, jsonObject);
        JUtils.getArray(JKey.DEVICE_DEVICE, jsonObject);
        for (final DeviceItem device : devices) {
            exportDeviceItem(device, jsonObject);
        }
    }

    /**
     * @param device device item to export
     * @param jsonObject json to export to
     * @throws JSONException json exception
     */
    private static void exportDeviceItem(final DeviceItem device, final JSONObject jsonObject) throws JSONException {
        final JSONObject json = new JSONObject();
        // device[ id, name, notes, uid, lastUpdate, vendor, version, modelNumber, troubleshooting, deviceItemsProps ]
        // device[ protocolVerRange, ~template, ~hidden, ~equipment, ~certified ]
        json.put(JKey.CERTIFIED, device.isCertified());
        json.put(JKey.DEVICE_ITEMS_PROPS, device.getDeviceItemsProps());
        json.put(JKey.EQUIPMENT, device.isEquipment());
        json.put(JKey.HIDDEN, device.isHidden());
        json.put(JKey.ID, device.getId());
        json.put(JKey.MODEL_NUMBER, device.getModelNumber());
        json.put(JKey.NAME, device.getName());
        json.put(JKey.NOTES, device.getNotes());
        json.put(JKey.PROTOCOL_VER_RANGE, device.getProtocolVerRange());
        json.put(JKey.TEMPLATE, device.isTemplate());
        json.put(JKey.TROUBLESHOOTING, device.getTroubleshooting());
        json.put(JKey.UID, device.getUid());
        json.put(JKey.VENDOR, device.getVendor());
        json.put(JKey.VERSION, device.getVersion());
        JUtils.putTimestamp(JKey.LAST_UPDATE, device.getLastUpdate(), json);

        exportLookup(device, json);
        JUtils.getArray(JKey.DEVICES, jsonObject).put(json);

        // device_device
        for (final DeviceItem child : device.getChildren()) {
            final JSONArray parentChild = new JSONArray();
            parentChild.put(device.getId()).put(child.getId());
            JUtils.getArray(JKey.DEVICE_DEVICE, jsonObject).put(parentChild);
        }
    }

    /**
     * @param device device item to export
     * @param json json to export to
     * @throws JSONException json exception
     */
    private static void exportLookup(final DeviceItem device, final JSONObject json) throws JSONException {
        // lookup [ _masterTemplateName_, _masterTemplateVendor_, _masterTemplateModelNumber_, _masterTemplateVersion_ ]
        final DeviceItem template = device.getMasterTemplate();
        if (template != null) {
            json.put(JKey._MASTER_TEMPLATE_NAME_, template.getName());
            json.put(JKey._MASTER_TEMPLATE_VENDOR_, template.getVendor());
            json.put(JKey._MASTER_TEMPLATE_MODEL_NUMBER_, template.getModelNumber());
            json.put(JKey._MASTER_TEMPLATE_VERSION_, template.getVersion());
        }

        // lookup [ _zoneId_, _lastUpdateUserEmail_ ]
        if (device.getLastUpdateUser() != null) {
            json.put(JKey._LAST_UPDATE_USER_EMAIL_, device.getLastUpdateUser().getEmail());
        }
        if (device.getZone() != null) {
            json.put(JKey._ZONE_ID_, device.getZone().getId());
        }

        // lookup [ _deviceTypes_, _deviceTypeName_, _deviceCategoryName_ ]
        final JSONArray jsonDeviceTypes = JUtils.getArray(JKey._DEVICE_TYPES_, json);
        for (final DeviceType deviceType : device.getDeviceTypes()) {
            final JSONObject jsonDeviceType = new JSONObject();
            jsonDeviceType.put(JKey._DEVICE_TYPE_NAME_, deviceType.getName());
            if (deviceType.getDeviceCategory() != null) {
                jsonDeviceType.put(JKey._DEVICE_CATEGORY_NAME_, deviceType.getDeviceCategory().getName());
            }
            jsonDeviceTypes.put(jsonDeviceType);
        }

        // lookup [ _protocolAdapterName_, _protocolAdapterVersion_ ]
        json.put(JKey._PROTOCOL_ADAPTER_NAME_, device.getProtocolAdapter().getName());
        json.put(JKey._PROTOCOL_ADAPTER_VERSION_, device.getProtocolAdapter().getVersion());

        // lookup [ _deviceClasses_ ]
        final JSONArray jsonDeviceClasses = JUtils.getArray(JKey._DEVICE_CLASSES_, json);
        for (final DeviceClass deviceClass : device.getDeviceClasses()) {
            jsonDeviceClasses.put(deviceClass.getName());
        }
    }

    /**
     * @param manager import manager
     * @param jsonObject json object to import
     * @throws JSONException json exception
     */
    static void importDeviceItems(final ImportManager manager, final JSONObject jsonObject) throws JSONException {
        Objects.requireNonNull(manager);
        Objects.requireNonNull(jsonObject);

        final Map<Integer, DeviceItem> idDeviceMap = manager.getIdDeviceMap();
        idDeviceMap.clear();
        final JSONArray devices = jsonObject.optJSONArray(JKey.DEVICES);
        if (devices != null) {
            for (int index = 0, size = devices.length(); index < size; index++) {
                importDeviceItem(manager, devices.getJSONObject(index));
            }
        }
        // import device_device
        final JSONArray deviceDevices = jsonObject.optJSONArray(JKey.DEVICE_DEVICE);
        if (deviceDevices != null) {
            for (int index = 0, size = deviceDevices.length(); index < size; index++) {
                final JSONArray parentChild = deviceDevices.getJSONArray(index);
                final DeviceItem parent = idDeviceMap.get(parentChild.getInt(0));
                final DeviceItem child = idDeviceMap.get(parentChild.getInt(1));
                // parent.getChildren().add(child); // this way will not work right.
                child.setParent(parent);
            }
        }
    }

    /**
     * @param manager import manager
     * @param json json object to import from
     * @throws JSONException json exception
     */
    private static void importDeviceItem(final ImportManager manager, final JSONObject json) throws JSONException {
        final Integer idDevice = json.getInt(JKey.ID);
        if (!manager.getIdDeviceMap().containsKey(idDevice)) {
            final DeviceItem device = new DeviceItem();
            device.setCertified(json.optBoolean(JKey.CERTIFIED));
            device.setDeviceItemsProps(JUtils.optStringStringMap(JKey.DEVICE_ITEMS_PROPS, json));
            device.setEquipment(json.optBoolean(JKey.EQUIPMENT));
            device.setHidden(json.optBoolean(JKey.HIDDEN));
            device.setLastUpdate(JUtils.optTimestamp(JKey.LAST_UPDATE, json));
            device.setModelNumber(json.optString(JKey.MODEL_NUMBER, null));
            device.setName(json.optString(JKey.NAME, null));
            device.setNotes(json.optString(JKey.NOTES, null));
            device.setProtocolVerRange(json.optString(JKey.PROTOCOL_VER_RANGE, null));
            device.setTemplate(json.optBoolean(JKey.TEMPLATE));
            device.setTroubleshooting(json.optString(JKey.TROUBLESHOOTING, null));
            device.setUid(json.optString(JKey.UID, null));
            device.setVendor(json.optString(JKey.VENDOR, null));
            device.setVersion(json.optString(JKey.VERSION, null));
            importLookup(device, manager, json);
            device.setProjectVersion(manager.getVersion());
            manager.getIdDeviceMap().put(idDevice, device);
        }
    }

    /**
     * @param device device to import
     * @param manager import manager
     * @param json json to import from
     * @param uidZoneMap zone-uid, zone map
     * @throws JSONException
     */
    private static void importLookup(final DeviceItem device, final ImportManager manager, final JSONObject json)
        throws JSONException {
        // key [ _importTemplateId_ ]
        if (json.has(JKey._IMPORT_TEMPLATE_ID_)) {
            device.setMasterTemplate(manager.findEntity(DeviceItem.class, json.getInt(JKey._IMPORT_TEMPLATE_ID_)));
        } else if (json.has(JKey._MASTER_TEMPLATE_NAME_)) {
            final String name = json.optString(JKey._MASTER_TEMPLATE_NAME_, null);
            final String vendor = json.optString(JKey._MASTER_TEMPLATE_VENDOR_, null);
            final String modelNumber = json.optString(JKey._MASTER_TEMPLATE_MODEL_NUMBER_, null);
            final String version = json.optString(JKey._MASTER_TEMPLATE_VERSION_, null);
            device.setMasterTemplate(manager.lookupTemplate(name, vendor, modelNumber, version));
        }
        if (json.has(JKey._ZONE_ID_)) {
            device.setZone(manager.getIdZoneMap().get(json.getInt(JKey._ZONE_ID_)));
        }
        device.setLastUpdateUser(manager.lookupUser(json.optString(JKey._LAST_UPDATE_USER_EMAIL_, null)));
        final JSONArray jsonDeviceTypes = json.optJSONArray(JKey._DEVICE_TYPES_);
        if (jsonDeviceTypes != null) {
            for (int index = 0, size = jsonDeviceTypes.length(); index < size; index++) {
                final JSONObject jsonDeviceType = jsonDeviceTypes.getJSONObject(index);
                final DeviceType deviceType =
                    manager.lookupDeviceType(jsonDeviceType.optString(JKey._DEVICE_TYPE_NAME_, null), jsonDeviceType
                        .optString(JKey._DEVICE_CATEGORY_NAME_, null));
                if (deviceType != null) {
                    device.getDeviceTypes().add(deviceType);
                }
            }
        }
        device.setProtocolAdapter(manager.lookupProtocolAdapter(json.optString(JKey._PROTOCOL_ADAPTER_NAME_, null),
            json.optString(JKey._PROTOCOL_ADAPTER_VERSION_, null)));
        final JSONArray jsonDeviceClasses = json.optJSONArray(JKey._DEVICE_CLASSES_);
        if (jsonDeviceClasses != null) {
            for (int index = 0, size = jsonDeviceClasses.length(); index < size; index++) {
                device.getDeviceClasses().add(manager.lookupDeviceClass(jsonDeviceClasses.optString(index, null)));
            }
        }
    }

    /**
     * Private constructor.
     */
    private JDeviceItem() {
        // Utility class
    }
}
