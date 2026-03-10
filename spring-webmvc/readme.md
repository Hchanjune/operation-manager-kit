

## OMK Context Lifecycle With ServletContainer(Spring-webmvc)
***

### HttpRequest

↓

### Servlet Filters

[ OMK Filter ] : Telemetry Collect / Inject (MDC)
- traceId : from header traceparent ?: new traceId
- after doFilter : ThreadCleaning (clears MDC, context)

↓

### DispatcherServlet

↓

### HandlerMapping

↓

### HandlerInterceptor (preHandle)

[ OMK Interceptor (preHandle) ] : Metadata Collect / Inject (MDC)
- operationContext.entrypoint : Controller#method

↓

### Controller

↓

### ApplicationService (Must Execute Operations{} lambda block)

#### [OMK Annotation Managed] : AOP Managed by @OperationManaged
- operationContext.service: Service#method
- operationContext.operation: @OperationManaged.operation
- operationContext.useCase: @OperationManaged.useCase
- operationContext.event: @OperationManaged.event

#### [OMK Executor (lambda block)]

 - operationContext.issuer : from IssuerProvider (Optional) Spring Security Context ?: "anonymous"
 - operationContext.response : returning data
 - operationContext.message : returning message (modifiable)
 - operationContext.durationMs : duration processing lambda block
 - operationContext:timestamp : Instant.now()
 - operationContext.attributes : accessible in lambda block
 - metricContext : from WebMvcMetricsContextProvider: MetricContextProvider
 - telemetryContext: from (Optional)Otel / TelemetryContextProviderImpl :TelemetryContextProvider
 - 
↓

### Repository / External Systems

↓

#### [When OMK Executor success]
 - metricContext.classify
 - returns

#### [When OMK Executor Fails]
 - metricContext.classify
 - throws Exception: May flow to ControllerAdvice

↓

### HandlerInterceptor (postHandle)

↓

### View Rendering / Response Serialization

↓

### HandlerInterceptor (afterCompletion)

#### [ OMK Interceptor (afterCompletion) ] : Cleans MDC

↓

### HttpResponse
***