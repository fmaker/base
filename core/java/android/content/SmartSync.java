/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content;

import android.os.Parcelable;
import android.os.Bundle;
import android.os.Parcel;
import android.accounts.Account;

/**
 * Value type that contains information about a smart sync. Is parcelable, making it suitable
 * for passing in an IPC.
 */
public class SmartSync implements Parcelable {
    /** The account to be synced */
    public final Account account;
    /** The authority of the sync */
    public final String authority;
    /** Any extras that parameters that are to be passed to the sync adapter. */
    public final Bundle extras;
    /** The minimum period the sync should be scheduled, in seconds. */
    public final long minPeriod;
    /** The maximum period the sync should be scheduled, in seconds. */
    public final long maxPeriod;

    /** Creates a new SmartSync, copying the Bundle */
    public SmartSync(Account account, String authority, Bundle extras, long minPeriod, long maxPeriod) {
        this.account = account;
        this.authority = authority;
        this.extras = new Bundle(extras);
        this.minPeriod = minPeriod;
        this.maxPeriod = maxPeriod;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        account.writeToParcel(dest, flags);
        dest.writeString(authority);
        dest.writeBundle(extras);
        dest.writeLong(minPeriod);
        dest.writeLong(maxPeriod);
    }

    public static final Creator<SmartSync> CREATOR = new Creator<SmartSync>() {
        public SmartSync createFromParcel(Parcel source) {
            return new SmartSync(Account.CREATOR.createFromParcel(source),
                    source.readString(), source.readBundle(), source.readLong(), source.readLong());
        }

        public SmartSync[] newArray(int size) {
            return new SmartSync[size];
        }
    };

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof SmartSync)) {
            return false;
        }

        final SmartSync other = (SmartSync) o;

        return account.equals(other.account)
                && authority.equals(other.authority)
                && minPeriod == other.minPeriod
                && maxPeriod == other.maxPeriod
                && SyncStorageEngine.equals(extras, other.extras);
    }
}
