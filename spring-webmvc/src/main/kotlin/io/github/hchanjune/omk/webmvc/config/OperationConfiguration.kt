package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationListener
import io.github.hchanjune.omk.webmvc.Operations
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.AnnotationAwareOrderComparator

@Configuration
internal class OperationConfiguration {

//    /**
//     * ###### Operation Listener
//     */
//    @Bean(name = ["operationCompositeListener"])
//    @Primary
//    @ConditionalOnMissingBean(/* ...value = */ CompositeOperationListener::class)
//    fun operationCompositeListener(
//        provider: ObjectProvider<List<OperationListener>>
//    ): OperationListener {
//
//        val listeners = (provider.ifAvailable ?: emptyList())
//            .filterNot { it is CompositeOperationListener }
//
//        val ordered = listeners.toMutableList().apply {
//            AnnotationAwareOrderComparator.sort(this)
//        }
//
//        return CompositeOperationListener(ordered)
//    }

    /**
     * ###### OperationExecutor
     */
    @Bean
    fun operationExecutor(): OperationExecutor =
        OperationExecutor()

    /**
     * ###### OperationExecutor Initializer
     */
    @Bean
    fun operationInitializer(executor: OperationExecutor): Any {
        Operations.configure(executor)
        return Any()
    }

}