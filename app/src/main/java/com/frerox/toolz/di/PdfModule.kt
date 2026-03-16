package com.frerox.toolz.di

import android.content.Context
import com.frerox.toolz.data.pdf.PdfRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PdfModule {
    
    @Provides
    @Singleton
    fun providePdfRepository(@ApplicationContext context: Context): PdfRepository {
        return PdfRepository(context)
    }
}
