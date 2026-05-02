package com.example.sarisaristore.util

import android.content.Context
import com.example.sarisaristore.AppContainer
import com.example.sarisaristore.SariSariApplication

fun Context.appContainer(): AppContainer =
    (applicationContext as SariSariApplication).container
