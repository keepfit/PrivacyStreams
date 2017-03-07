package com.github.privacystreams.core;

import android.content.Context;

import com.github.privacystreams.core.exceptions.PermissionDeniedException;
import com.github.privacystreams.core.exceptions.PrivacyStreamsException;
import com.github.privacystreams.core.providers.MultiItemStreamProvider;
import com.github.privacystreams.core.providers.SingleItemStreamProvider;
import com.github.privacystreams.core.purposes.Purpose;
import com.github.privacystreams.core.utils.Logging;
import com.github.privacystreams.core.utils.permission.PermissionUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Set;


/**
 * The unified query interface for all kinds of personal data.
 * You will need to construct an UQI with <code>UQI uqi = new UQI(context);</code>
 * Then, to get multi-item stream data, call <code>uqi.getDataItems</code> ({@link #getDataItems(MultiItemStreamProvider, Purpose)});
 * To get single-item data, call <code>uqi.getDataItem</code> ({@link #getDataItem(SingleItemStreamProvider, Purpose)}).
 */

public class UQI {
    private Purpose purpose;
    public Purpose getPurpose() {
        return this.purpose;
    }

    private Function<Void, Void> query;
    public Function<Void, Void> getQuery() {
        return this.query;
    }
    void setQuery(Function<Void, Void> query) { this.query = query; }

    private transient Context context;
    public Context getContext() {
        return this.context;
    }
    public void setContext(Context context) { this.context = context; }

    private transient Gson gson;
    public Gson getGson() {
        return this.gson;
    }

    private transient String uuid;
    public String getUUID() {
        if (this.uuid == null) {
            // TODO change this to other identifier as using device id is not privacy friendly.
//            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//            this.uuid = tm.getDeviceId();
            this.uuid = "uuid_test";
        }
        return this.uuid;
    }

    private transient PrivacyStreamsException exception;
    public PrivacyStreamsException getException() {
        return exception;
    }

    private transient boolean streamDebug = false;
    public void setStreamDebug(boolean streamDebug) {
        this.streamDebug = streamDebug;
    }
    public boolean isStreamDebug() {
        return streamDebug;
    }

    public UQI(Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.purpose = null;
        this.query = null;
        this.uuid = null;
    }

    private UQI(Context context, Purpose purpose) {
        this(context);
        this.purpose = purpose;
    }

    private UQI getUQIWithPurpose(Purpose newPurpose) {
        if (this.purpose == null) this.purpose = newPurpose;
        if (this.purpose == newPurpose) return this;
        return new UQI(this.context, newPurpose);
    }

    /**
     * Get a multi-item personal data stream from a provider with a purpose.
     * For example, using <code>uqi.getDataItems(Contact.asList(), Purpose.feature("..."))</code> will return a stream of contacts.
     * @param mStreamProvider the function to provide the personal data stream, e.g. GeoLocation.asUpdates(), SMS.asHistory().
     * @param purpose the purpose of personal data use, e.g. Purpose.ads("xxx").
     * @return the personal data stream
     */
    public IMultiItemStream getDataItems(MultiItemStreamProvider mStreamProvider, Purpose purpose) {
        UQI uqi = this.getUQIWithPurpose(purpose);
        return new MultiItemStream(uqi, mStreamProvider);
    }

    /**
     * Get a single-item personal data item from a provider with a purpose
     * For example, using <code>uqi.getDataItem(Location.asLastKnown(), Purpose.feature("..."))</code> will return a stream that contains one location item.
     * @param sStreamProvider the function to provide the personal data item, e.g. Location.asLastKnown(), Audio.record(100).
     * @param purpose the purpose of personal data use, e.g. Purpose.ads("xxx").
     * @return the personal data item
     */
    public ISingleItemStream getDataItem(SingleItemStreamProvider sStreamProvider, Purpose purpose) {
        UQI uqi = this.getUQIWithPurpose(purpose);
        return new SingleItemStream(uqi, sStreamProvider);
    }

    /**
     * Evaluate current UQI.
     *
     * @param retry whether to try again if the permission is denied.
     */
    public void evaluate(boolean retry) {
        Logging.debug("Trying to evaluate PrivacyStreams Query.");
        Logging.debug("Purpose: " + this.purpose);
        Logging.debug("Query: " + this.query);
        Logging.debug("Required Permissions: " + this.query.getRequiredPermissions());

        if (PermissionUtils.checkPermissions(this.context, this.query.getRequiredPermissions())) {
            Logging.debug("Evaluating...");
            this.query.apply(this, null);
        }
        else if (retry) {
            // If retry is true, try to request permissions
            Logging.debug("Permission denied, retrying...");
            PermissionUtils.requestPermissionAndEvaluate(this);
        }
        else {
            // If retry is false, cancel all functions.
            Logging.debug("Permission denied, cancelling...");
            Set<String> deniedPermissions = PermissionUtils.getDeniedPermissions(this.context, this.query.getRequiredPermissions());
            this.exception = new PermissionDeniedException(deniedPermissions.toArray(new String[]{}));
            this.query.cancel(this);
            this.context = null; // remove context
        }
    }
}
