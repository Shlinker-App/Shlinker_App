package app.shlinker

import android.content.Context
import app.shlinker.core.datastore.DefaultAppTagName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Values that differ per release channel; see `app/src/<channel>/res/values/channel.xml`. */
@Module
@InstallIn(SingletonComponent::class)
object ChannelModule {
    @Provides
    @DefaultAppTagName
    fun provideDefaultAppTagName(@ApplicationContext context: Context): String =
        context.getString(R.string.default_app_tag)
}
