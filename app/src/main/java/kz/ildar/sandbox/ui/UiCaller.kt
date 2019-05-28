/**
 * (C) Copyright 2019 Ildar Ishalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package kz.ildar.sandbox.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.ildar.sandbox.data.RequestResult
import kz.ildar.sandbox.di.CoroutineProvider
import kz.ildar.sandbox.utils.EventWrapper
import kz.ildar.sandbox.utils.ResourceString

interface UiProvider {
    val statusLiveData: LiveData<Status>
    val errorLiveData: LiveData<EventWrapper<ResourceString>>
}

interface UiCaller : UiProvider {
    fun <T> makeRequest(
        call: suspend CoroutineScope.() -> T,
        resultBlock: (suspend (T) -> Unit)? = null
    )

    fun <T> unwrap(
        result: RequestResult<T>,
        errorBlock: ((ResourceString) -> Unit)? = { setError(it) },
        successBlock: (T) -> Unit
    ): Unit?

    fun set(status: Status)

    fun setError(error: ResourceString)
}

class UiCallerImpl(
    private val scope: CoroutineScope,
    private val scopeProvider: CoroutineProvider,
    _statusLiveData: MutableLiveData<Status>,
    _errorLiveData: MutableLiveData<EventWrapper<ResourceString>>
) : UiCaller {
    override val statusLiveData: MutableLiveData<Status> = _statusLiveData
    override val errorLiveData: MutableLiveData<EventWrapper<ResourceString>> = _errorLiveData

    /**
     * Presentation-layer-обработчик для запросов через `kotlin coroutines`:
     * запускает [Job] в [scope],
     * вызывает прогресс на [statusLiveData]
     *
     * [call] - `suspend`-функция запроса из репозитория
     * [resultBlock] - функция, которую нужно выполнить по завершении запроса в UI-потоке
     */
    override fun <T> makeRequest(
        call: suspend CoroutineScope.() -> T,
        resultBlock: (suspend (T) -> Unit)?
    ) {
        scope.launch(scopeProvider.Main) {
            set(Status.SHOW_LOADING)
            val result = withContext(scopeProvider.IO, call)
            resultBlock?.invoke(result)
            set(Status.HIDE_LOADING)
        }
    }

    override fun set(status: Status) {
        statusLiveData.postValue(status)
    }

    override fun setError(error: ResourceString) {
        errorLiveData.postValue(EventWrapper(error))
    }

    /**
     * Обработчик для ответов [RequestResult] репозитория.
     * [errorBlock] - функция обработки ошибок, можно передать `null`, чтобы никак не обрабатывать.
     * [successBlock] - обработка непустого результата
     */
    override fun <T> unwrap(
        result: RequestResult<T>,
        errorBlock: ((ResourceString) -> Unit)?,
        successBlock: (T) -> Unit
    ) = when (result) {
        is RequestResult.Success -> result.result?.let { successBlock(it) }
        is RequestResult.Error -> errorBlock?.invoke(result.error)
    }
}