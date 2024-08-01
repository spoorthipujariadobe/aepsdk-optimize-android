/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.optimize;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.StringUtils;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

public class Offer {

    private static final String SELF_TAG = "Offer";
    private String id;
    private String etag;
    private int score;
    private String schema;
    private Map<String, Object> meta;
    private OfferType type;
    private List<String> language;
    private String content;
    private Map<String, String> characteristics;

    SoftReference<OptimizeProposition> propositionReference;

    /**
     * Private constructor.
     *
     * <p>Use {@link Builder} to create {@link Offer} object.
     */
    private Offer() {}

    /** {@code Offer} Builder. */
    public static class Builder {
        private final Offer offer;
        private boolean didBuild;

        /**
         * Builder constructor with required {@code Offer} attributes as parameters.
         *
         * <p>It sets default values for remaining {@link Offer} attributes.
         *
         * @param id required {@link String} containing {@code Offer} identifier.
         * @param type required {@link OfferType} indicating the {@code Offer} type.
         * @param content required {@code String} containing the {@code Offer} content.
         */
        public Builder(final String id, final OfferType type, final String content) {
            offer = new Offer();
            offer.id = id != null ? id : "";
            offer.type = type != null ? type : OfferType.UNKNOWN;
            offer.content = content != null ? content : "";
            offer.etag = "";
            offer.score = 0;
            offer.schema = "";
            offer.meta = new HashMap<>();
            offer.language = new ArrayList<>();
            offer.characteristics = new HashMap<>();
            didBuild = false;
        }

        /**
         * Sets the etag for this {@code Offer}.
         *
         * @param etag {@link String} containing {@link Offer} etag.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setEtag(final String etag) {
            throwIfAlreadyBuilt();

            offer.etag = etag;
            return this;
        }

        /**
         * Sets the score for this {@code Offer}.
         *
         * @param score {@code int} containing {@link Offer} score.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setScore(final int score) {
            throwIfAlreadyBuilt();

            offer.score = score;
            return this;
        }

        /**
         * Sets the schema for this {@code Offer}.
         *
         * @param schema {@link String} containing {@link Offer} schema.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setSchema(final String schema) {
            throwIfAlreadyBuilt();

            offer.schema = schema;
            return this;
        }

        /**
         * Sets the metadata for this {@code Offer}.
         *
         * @param meta {@code Map<String, Object>} containing {@link Offer} metadata.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setMeta(final Map<String, Object> meta) {
            throwIfAlreadyBuilt();

            offer.meta = meta;
            return this;
        }

        /**
         * Sets the language for this {@code Offer}.
         *
         * @param language {@code List<String>} containing supported {@link Offer} language.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setLanguage(final List<String> language) {
            throwIfAlreadyBuilt();

            offer.language = language;
            return this;
        }

        /**
         * Sets the characteristics for this {@code Offer}.
         *
         * @param characteristics {@code Map<String, String>} containing {@link Offer}
         *     characteristics.
         * @return this Offer {@link Builder}
         * @throws UnsupportedOperationException if this method is invoked after {@link
         *     Builder#build()}.
         */
        public Builder setCharacteristics(final Map<String, String> characteristics) {
            throwIfAlreadyBuilt();

            offer.characteristics = characteristics;
            return this;
        }

        /**
         * Builds and returns the {@code Offer} object.
         *
         * @return {@link Offer} object or null.
         */
        public Offer build() {
            throwIfAlreadyBuilt();
            didBuild = true;

            return offer;
        }

        private void throwIfAlreadyBuilt() {
            if (didBuild) {
                throw new UnsupportedOperationException(
                        "Attempted to call methods on Offer.Builder after build() was invoked.");
            }
        }
    }

    /**
     * Gets the {@code Offer} identifier.
     *
     * @return {@link String} containing the {@link Offer} identifier.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the {@code Offer} etag.
     *
     * @return {@link String} containing the {@link Offer} etag.
     */
    public String getEtag() {
        return etag;
    }

    /**
     * Gets the {@code Offer} score.
     *
     * @return {@code int} containing the {@link Offer} score.
     */
    public int getScore() {
        return score;
    }

    /**
     * Gets the {@code Offer} schema.
     *
     * @return {@link String} containing the {@link Offer} schema.
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Gets the {@code Offer} metadata.
     *
     * @return {@code Map<String, Object>} containing the {@link Offer} metadata.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Gets the {@code Offer} type.
     *
     * @return {@link OfferType} indicating the {@link Offer} type.
     */
    public OfferType getType() {
        return type;
    }

    /**
     * Gets the {@code Offer} language.
     *
     * @return {@code List<String>} containing the supported {@link Offer} language.
     */
    public List<String> getLanguage() {
        return language;
    }

    /**
     * Gets the {@code Offer} content.
     *
     * @return {@link String} containing the {@link Offer} content.
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets the {@code Offer} characteristics.
     *
     * @return {@code Map<String, String>} containing the {@link Offer} characteristics.
     */
    public Map<String, String> getCharacteristics() {
        return characteristics;
    }

    /**
     * Gets the containing {@code OptimizeProposition} for this {@code Offer}.
     *
     * @return {@link OptimizeProposition} instance.
     */
    public OptimizeProposition getProposition() {
        return propositionReference.get();
    }

    /**
     * Dispatches an event for the Edge network extension to send an Experience Event to the Edge
     * network with the display interaction data for the given {@code OptimizeProposition} offer.
     *
     * @see Offer#trackWithData(Map)
     */
    public void displayed() {
        trackWithData(generateDisplayInteractionXdm());
    }

    /**
     * Dispatches an event for the Edge network extension to send an Experience Event to the Edge
     * network with the tap interaction data for the given {@code OptimizeProposition} offer.
     *
     * @see Offer#trackWithData(Map)
     */
    public void tapped() {
        trackWithData(generateTapInteractionXdm());
    }

    /**
     * Generates a map containing XDM formatted data for {@code Experience Event -
     * OptimizeProposition Interactions} field group from this {@code OptimizeProposition} item.
     *
     * <p>The returned XDM data does contain the {@code eventType} for the Experience Event with
     * value {@code decisioning.propositionDisplay}.
     *
     * <p>Note: The Edge sendEvent API can be used to dispatch this data in an Experience Event
     * along with any additional XDM, free-form data, and override dataset identifier.
     *
     * @return {@code Map<String, Object>} containing the XDM data for the proposition interaction.
     * @see Offer#generateInteractionXdm(String)
     */
    public Map<String, Object> generateDisplayInteractionXdm() {
        return generateInteractionXdm(
                OptimizeConstants.JsonValues.EE_EVENT_TYPE_PROPOSITION_DISPLAY);
    }

    /**
     * Generates a map containing XDM formatted data for {@code Experience Event -
     * OptimizeProposition Interactions} field group from this {@code OptimizeProposition} offer.
     *
     * <p>The returned XDM data contains the {@code eventType} for the Experience Event with value
     * {@code decisioning.propositionInteract}.
     *
     * <p>Note: The Edge sendEvent API can be used to dispatch this data in an Experience Event
     * along with any additional XDM, free-form data, and override dataset identifier.
     *
     * @return {@code Map<String, Object>} containing the XDM data for the proposition interaction.
     * @see Offer#generateInteractionXdm(String)
     */
    public Map<String, Object> generateTapInteractionXdm() {
        return generateInteractionXdm(
                OptimizeConstants.JsonValues.EE_EVENT_TYPE_PROPOSITION_INTERACT);
    }

    /**
     * Generates a map containing XDM formatted data for {@code Experience Event -
     * OptimizeProposition Interactions} field group from this {@code OptimizeProposition} offer and
     * given {@code experienceEventType}.
     *
     * <p>The method returns null if the proposition reference within the offer is released and no
     * longer valid.
     *
     * @param experienceEventType {@link String} containing the event type for the Experience Event
     * @return {@code Map<String, Object>} containing the XDM data for the proposition interaction.
     */
    private Map<String, Object> generateInteractionXdm(final String experienceEventType) {
        if (propositionReference == null || propositionReference.get() == null) {
            return null;
        }

        OptimizeProposition optimizeProposition = propositionReference.get();
        final Map<String, Object> propositionsData = new HashMap<>();
        propositionsData.put(
                OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS_ID,
                optimizeProposition.getId());
        propositionsData.put(
                OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS_SCOPE,
                optimizeProposition.getScope());
        propositionsData.put(
                OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS_SCOPEDETAILS,
                optimizeProposition.getScopeDetails());

        final Map<String, Object> propositionItem = new HashMap<>();
        propositionItem.put(OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS_ITEMS_ID, id);

        final List<Map<String, Object>> propositionItemsList = new ArrayList<>();
        propositionItemsList.add(propositionItem);

        // Add list containing proposition item ids.
        propositionsData.put(
                OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS_ITEMS, propositionItemsList);

        final List<Map<String, Object>> decisioningPropositions = new ArrayList<>();
        decisioningPropositions.add(propositionsData);

        final Map<String, Object> experienceDecisioning = new HashMap<>();
        experienceDecisioning.put(
                OptimizeConstants.JsonKeys.DECISIONING_PROPOSITIONS, decisioningPropositions);

        final Map<String, Object> experience = new HashMap<>();
        experience.put(OptimizeConstants.JsonKeys.EXPERIENCE_DECISIONING, experienceDecisioning);

        final Map<String, Object> xdm = new HashMap<>();
        xdm.put(OptimizeConstants.JsonKeys.EXPERIENCE, experience);
        xdm.put(OptimizeConstants.JsonKeys.EXPERIENCE_EVENT_TYPE, experienceEventType);

        return xdm;
    }

    /**
     * Dispatches an event to track propositions with type {@value
     * OptimizeConstants.EventType#OPTIMIZE} and source {@value
     * OptimizeConstants.EventSource#REQUEST_CONTENT}.
     *
     * <p>No event is dispatched if the provided {@code xdm} is null or empty.
     *
     * @param xdm {@code Map<String, Object>} containing the XDM data for the proposition
     *     interactions.
     */
    private void trackWithData(final Map<String, Object> xdm) {
        if (OptimizeUtils.isNullOrEmpty(xdm)) {
            Log.debug(
                    OptimizeConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to dispatch track propositions request event, input xdm is null or"
                            + " empty.");
            return;
        }

        final Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                OptimizeConstants.EventDataKeys.REQUEST_TYPE,
                OptimizeConstants.EventDataValues.REQUEST_TYPE_TRACK);
        eventData.put(OptimizeConstants.EventDataKeys.PROPOSITION_INTERACTIONS, xdm);

        final Event edgeEvent =
                new Event.Builder(
                                OptimizeConstants.EventNames.TRACK_PROPOSITIONS_REQUEST,
                                OptimizeConstants.EventType.OPTIMIZE,
                                OptimizeConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        MobileCore.dispatchEvent(edgeEvent);
    }

    /**
     * Creates an {@code Offer} object using information provided in {@code data} map.
     *
     * <p>This method returns null if the provided {@code data} is empty or null or if it does not
     * contain required info for creating an {@link Offer} object.
     *
     * @param data {@code Map<String, Object>} containing offer data.
     * @return {@code Offer} object or null.
     */
    // todo check parity of this with iOS
    static Offer fromEventData(final Map<String, Object> data) {
        if (OptimizeUtils.isNullOrEmpty(data)) {
            Log.debug(
                    OptimizeConstants.LOG_TAG,
                    SELF_TAG,
                    "Cannot create Offer object, provided data Map is empty or null.");
            return null;
        }

        try {
            final String id =
                    DataReader.getString(data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_ID);
            final String etag =
                    DataReader.getString(data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_ETAG);
            final int score =
                    DataReader.optInt(data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_SCORE, 0);

            // todo: check if schema is mandatory
            final String schema =
                    DataReader.getString(data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_SCHEMA);

            final Map<String, Object> meta =
                    DataReader.getTypedMap(
                            Object.class, data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_META);

            final Map<String, Object> offerData =
                    DataReader.getTypedMap(
                            Object.class, data, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA);

            if (!OptimizeUtils.isNullOrEmpty(offerData)) {
                final String nestedId =
                        DataReader.getString(offerData, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_ID);
                if (OptimizeUtils.isNullOrEmpty(id) || !id.equals(nestedId)) {
                    Log.debug(
                            OptimizeConstants.LOG_TAG,
                            SELF_TAG,
                            "Cannot create Offer object, provided item id is null or empty or it"
                                    + " doesn't match item data id.");
                    return null;
                }

                final String format =
                        DataReader.getString(offerData, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_FORMAT);

                // todo can be simplified to OfferType.from(format)
                final OfferType offerType;
                if (format != null) {
                    offerType = OfferType.from(format);
                } else {
                    final String type = DataReader.optString(offerData, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_TYPE, null);
                    offerType = type == null ? OfferType.UNKNOWN : OfferType.from(type);
                }

                final List<String> language =
                        DataReader.getStringList(
                                offerData, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_LANGUAGE);
                final Map<String, String> characteristics =
                        DataReader.getStringMap(
                                offerData,
                                OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_CHARACTERISTICS);

                String content = null;
                if (offerData.containsKey(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_CONTENT)) {
                    content = getContentFromOfferData(offerData);
                } else if (offerData.containsKey(
                        OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_DELIVERYURL)) {
                    content =
                            DataReader.optString(offerData, OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_DELIVERYURL, null);
                }
                if (content == null) {
                    Log.debug(
                            OptimizeConstants.LOG_TAG,
                            SELF_TAG,
                            "Cannot create Offer object, provided data Map doesn't contain valid"
                                    + " item data content or deliveryURL.");
                    return null;
                }

                return new Builder(id, offerType, content)
                        .setEtag(etag)
                        .setScore(score)
                        .setSchema(schema)
                        .setMeta(meta)
                        .setLanguage(language)
                        .setCharacteristics(characteristics)
                        .build();
            } else {
                if (!schema.equals(OptimizeConstants.JsonValues.SCHEMA_TARGET_DEFAULT)) {
                    Log.debug(
                            OptimizeConstants.LOG_TAG,
                            SELF_TAG,
                            "Cannot create Offer object, provided data Map doesn't contain valid"
                                    + " item data.");
                    return null;
                }
                Log.trace(
                        OptimizeConstants.LOG_TAG,
                        SELF_TAG,
                        "Received default content proposition item, Offer content will be set to"
                                + " empty string.");
                return new Builder(id, OfferType.UNKNOWN, "")
                        .setEtag(null)
                        .setScore(0)
                        .setSchema(schema)
                        .setMeta(meta)
                        .setLanguage(null)
                        .setCharacteristics(null)
                        .build();
            }
        } catch (final ClassCastException | DataReaderException e) {
            Log.warning(
                    OptimizeConstants.LOG_TAG,
                    SELF_TAG,
                    "Cannot create Offer object, provided data contains invalid fields.");
            return null;
        }
    }

    /**
     * Creates a {@code Map<String, Object>} using this {@code Offer}'s attributes.
     *
     * @return {@code Map<String, Object>} containing {@link Offer} data.
     */
    Map<String, Object> toEventData() {
        final Map<String, Object> offerMap = new HashMap<>();
        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_ID, this.id);
        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_ETAG, this.etag);
        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_SCORE, this.score);
        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_SCHEMA, this.schema);
        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_META, this.meta);

        final Map<String, Object> data = new HashMap<>();
        data.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_ID, this.id);
        data.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_TYPE, this.type.toString());
        data.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_CONTENT, this.content);
        data.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_LANGUAGE, this.language);
        data.put(
                OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_CHARACTERISTICS, this.characteristics);

        offerMap.put(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA, data);
        return offerMap;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Offer that = (Offer) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (score != that.score) return false;
        if (etag != null ? !etag.equals(that.etag) : that.etag != null) return false;
        if (schema != null ? !schema.equals(that.schema) : that.schema != null) return false;
        if (meta != null ? !meta.equals(that.meta) : that.meta != null) return false;
        if (type != that.type) return false;
        if (language != null ? !language.equals(that.language) : that.language != null)
            return false;
        if (content != null ? !content.equals(that.content) : that.content != null) return false;
        return characteristics != null
                ? characteristics.equals(that.characteristics)
                : that.characteristics == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, etag, score, schema, type, language, content, characteristics);
    }

    // todo verify this logic with iOS
    private static String getContentFromOfferData(final Map<String, Object> offerData) {
        final String content;
        final Object offerContent =
                offerData.get(OptimizeConstants.JsonKeys.PAYLOAD_ITEM_DATA_CONTENT);
        if (offerContent instanceof String) {
            content = (String) offerContent;
        } else {
            final JSONObject offerContentJson = new JSONObject((Map<String, Object>) offerContent);
            content = offerContentJson.toString();
        }
        return content;
    }
}
