package com.clarecontrols.equator.solstice.api.beta;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.clarecontrols.equator.solstice.db.entities.rules.CFNode;
import com.clarecontrols.equator.solstice.db.entities.rules.CFProvider;

/**
 * JCFNode represents export/import CFNode helper.
 */
final class JCFNode {

    /**
     * @param cfNodes CFNodes to export
     * @param jsonObject json to export to
     * @throws JSONException json exception
     */
    static void exportCFNodes(final Set<CFNode> cfNodes, final JSONObject jsonObject) throws JSONException {
        Objects.requireNonNull(cfNodes);
        Objects.requireNonNull(jsonObject);

        // keys [cfnodes, cfnode_cfnode]
        JUtils.getArray(JKey.CFNODES, jsonObject);
        JUtils.getArray(JKey.CFNODE_CFNODE, jsonObject);
        final Set<CFNode> exportedNodes = new HashSet<CFNode>();
        for (final CFNode cfNode : cfNodes) {
            JCFNode.exportCFNode(exportedNodes, cfNode, jsonObject);
        }
    }

    /**
     * @param exportedNodes exported nodes
     * @param cfNode CFNode to export
     * @param jsonObject json object to export to
     * @throws JSONException json exception
     */
    private static void exportCFNode(final Set<CFNode> exportedNodes, final CFNode cfNode, final JSONObject jsonObject)
        throws JSONException {
        if (!exportedNodes.contains(cfNode)) {
            final JSONObject json = new JSONObject();
            // cfNode[ id, name, notes, uid ]
            json.put(JKey.ID, cfNode.getId());
            json.put(JKey.NAME, cfNode.getName());
            json.put(JKey.NOTES, cfNode.getNotes());
            json.put(JKey.UID, cfNode.getUid());

            final JSONArray jsonProperties = JUtils.getArray(JKey.PROPERTIES, json);
            for (final Map.Entry<String, Serializable> property : cfNode.getProperties().entrySet()) {
                exportProperty(property, jsonProperties);
            }

            exportLookup(cfNode, json);
            JUtils.getArray(JKey.CFNODES, jsonObject).put(json);
            exportedNodes.add(cfNode);

            // cfnode_cfnode
            for (final CFNode child : cfNode.getChildren()) {
                final JSONArray parentChild = new JSONArray();
                parentChild.put(cfNode.getId()).put(child.getId());
                JUtils.getArray(JKey.CFNODE_CFNODE, jsonObject).put(parentChild);
                exportCFNode(exportedNodes, child, jsonObject);
            }
        }
    }

    /**
     * @param property CFNode property to export
     * @param jsonProperties json array to exprot to
     * @throws JSONException json exception
     */
    private static void exportProperty(final Entry<String, Serializable> property, final JSONArray jsonProperties)
        throws JSONException {
        // reference: SerializerRules.getValueType() method
        // keys [ key, value, type ]
        final String key = property.getKey();
        final Serializable value = property.getValue();
        String type = null;
        if (value instanceof Float) {
            type = TYPE_FLOAT;
        } else if (value instanceof Double) {
            type = TYPE_DOUBLE;
        } else if (value instanceof Boolean) {
            type = TYPE_BOOLEAN;
        } else if (value instanceof String) {
            type = TYPE_STRING;
        } else if (value instanceof Long) {
            type = TYPE_LONG;
        } else if (value instanceof Integer) {
            type = TYPE_INT;
        } else if (value instanceof String[]) {
            type = TYPE_STRING_ARRAY;
        }
        final JSONObject json = new JSONObject();
        json.put(JKey.KEY, key);
        json.put(JKey.VALUE, value);
        json.put(JKey.TYPE, type);
        jsonProperties.put(json);
    }

    /**
     * @param cfNode CFNode to export
     * @param json json to export to
     * @throws JSONException json exception
     */
    private static void exportLookup(final CFNode cfNode, final JSONObject json) throws JSONException {
        // keys [ _providerName_, _providerTypeName_, _projectVersionId_ ]
        final CFProvider provider = cfNode.getProvider();
        if (provider != null) {
            json.put(JKey._PROVIDER_NAME_, provider.getName());
            json.put(JKey._PROVIDER_TYPE_NAME_, provider.getProviderType().name());
        }
        if (cfNode.getProjectVersion() != null) {
            json.put(JKey._PROJECT_VERSION_ID_, cfNode.getProjectVersion().getId());
        }
    }

    /**
     * @param manager import manager
     * @param jsonObject json to import CFNodes
     * @throws JSONException json exception
     */
    static void importCFNodes(final ImportManager manager, final JSONObject jsonObject) throws JSONException {
        Objects.requireNonNull(manager);
        Objects.requireNonNull(jsonObject);

        final Map<Integer, CFNode> idCFNodeMap = manager.getIdCFNodeMap();
        idCFNodeMap.clear();
        final JSONArray cfNodes = jsonObject.optJSONArray(JKey.CFNODES);
        if (cfNodes != null) {
            for (int index = 0, size = cfNodes.length(); index < size; index++) {
                importCFNode(manager, cfNodes.getJSONObject(index));
            }
        }
        // import cfnode_cfnode
        final JSONArray nodeNode = jsonObject.optJSONArray(JKey.CFNODE_CFNODE);
        if (nodeNode != null) {
            for (int index = 0, size = nodeNode.length(); index < size; index++) {
                final JSONArray parentChild = nodeNode.getJSONArray(index);
                final CFNode parent = idCFNodeMap.get(parentChild.getInt(0));
                final CFNode child = idCFNodeMap.get(parentChild.getInt(1));
                child.setParent(parent);
            }
        }
    }

    /**
     * @param manager import manager
     * @param json json to import cfNode
     * @throws JSONException json exception
     */
    private static void importCFNode(final ImportManager manager, final JSONObject json) throws JSONException {
        final int cfNodeId = json.getInt(JKey.ID);
        if (!manager.getIdCFNodeMap().containsKey(cfNodeId)) {
            final CFNode cfNode = new CFNode();
            cfNode.setName(json.optString(JKey.NAME, null));
            cfNode.setNotes(json.optString(JKey.NOTES, null));
            cfNode.setUid(json.optString(JKey.UID, null));
            if (json.has(JKey._PROJECT_VERSION_ID_)) {
                cfNode.setProjectVersion(manager.getVersion());
            }
            if (json.has(JKey._PROVIDER_NAME_)) {
                cfNode.setProvider(manager.lookupCFProvider(json.optString(JKey._PROVIDER_NAME_, null), json.optString(
                    JKey._PROVIDER_TYPE_NAME_, null)));
            }
            importProperties(cfNode, json.getJSONArray(JKey.PROPERTIES));
            manager.getIdCFNodeMap().put(cfNodeId, cfNode);
        }
    }

    /**
     * @param cfNode CFNode to import properties to
     * @param optJSONArray json array contains CFNode properties
     * @throws JSONException json exception
     */
    private static void importProperties(final CFNode cfNode, final JSONArray jsonArray) throws JSONException {
        for (int index = 0, size = jsonArray.length(); index < size; index++) {
            final JSONObject json = jsonArray.getJSONObject(index);
            final String key = json.optString(JKey.KEY, null);
            final String value = json.optString(JKey.VALUE, null);
            final String type = json.optString(JKey.TYPE, null);
            if (TYPE_FLOAT.equals(type)) {
                cfNode.getProperties().put(key, Float.parseFloat(value));
            } else if (TYPE_DOUBLE.equals(type)) {
                cfNode.getProperties().put(key, Double.parseDouble(value));
            } else if (TYPE_BOOLEAN.equals(type)) {
                cfNode.getProperties().put(key, Boolean.parseBoolean(value));
            } else if (TYPE_STRING.equals(type)) {
                cfNode.getProperties().put(key, value);
            } else if (TYPE_LONG.equals(type)) {
                cfNode.getProperties().put(key, Long.parseLong(value));
            } else if (TYPE_INT.equals(type)) {
                cfNode.getProperties().put(key, Integer.parseInt(value));
            } else if (TYPE_STRING_ARRAY.equals(type)) {
                cfNode.getProperties().put(key, JUtils.toStringArray(new JSONArray(value)));
            } else {
                cfNode.getProperties().put(key, value);
            }
        }
    }

    /**
     * Private constructor.
     */
    private JCFNode() {
        // Utility class
    }

    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_DOUBLE = "double";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_INT = "int";
    private static final String TYPE_STRING_ARRAY = "string[]";

}
