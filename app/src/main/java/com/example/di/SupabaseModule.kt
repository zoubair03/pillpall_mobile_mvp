package com.example.di

import android.content.Context
import com.example.BuildConfig
import com.example.data.auth.PillPalSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

  @Provides
  @Singleton
  fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
  ) {
    httpEngine = OkHttp.create()
    install(Auth) {
      // See PillPalSessionManager's doc comment — the SDK's own default
      // session manager didn't actually persist anything on Android here.
      sessionManager = PillPalSessionManager(context)
    }
    install(Postgrest)
    install(Realtime)
    install(Functions)
  }
}
