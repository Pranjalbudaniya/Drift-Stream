package com.drift.stream.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.drift.stream.domain.repository.ActiveProfileProvider
import kotlinx.coroutines.flow.Flow

class ProfileScopedDataStore(
    private val kind: PersonalStore,
    private val stores: ProfileDataStores,
    private val active: ActiveProfileProvider,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        active.scopedFlow { id -> stores.storeFor(kind, id).data }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val id = active.requireId()
        return stores.storeFor(kind, id).updateData(transform)
    }
}
