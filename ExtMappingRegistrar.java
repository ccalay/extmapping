import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class ExtMappingRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(ExtMappingRegistrar.class);
    private static final String MAPPING_BEAN_NAME = "requestMappingHandlerMapping";
    private static final String EXT_PREFIX = "/ext";
    private static final String PATH_DELIMITER = "/";

    private final ApplicationContext applicationContext;

    public ExtMappingRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void registerPrefixedMappings() {
        RequestMappingHandlerMapping handlerMapping = applicationContext.getBean(MAPPING_BEAN_NAME, RequestMappingHandlerMapping.class);

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

        handlerMethods.forEach((originalInfo, handlerMethod) -> {
            Method method = handlerMethod.getMethod();

            // ExtMappingli anatasyon var mı kontrol et
            if (!AnnotatedElementUtils.hasAnnotation(method, ExtMapping.class)) {
                return;
            }

            Set<String> originalPaths = extractPaths(originalInfo);
            if (originalPaths.isEmpty()) {
                logger.warn("No valid path found for method: {}", method.getName());
                return;
            }

            // Orjinal pathleri EXT_PREFIX ile prefixle
            Set<String> prefixedPaths = originalPaths.stream()
                    .map(path -> EXT_PREFIX + (path.startsWith(PATH_DELIMITER) ? path : PATH_DELIMITER + path))
                    .collect(Collectors.toSet());

            // Orjinal HTTP methodlarını al
            RequestMethod[] httpMethods = extractHttpMethods(originalInfo);

            // Http metodları ve prefixed pathleri kullanarak yeni RequestMappingInfo oluştur
            RequestMappingInfo.Builder builder = RequestMappingInfo.paths(prefixedPaths.toArray(new String[0]));
            if (httpMethods.length > 0) {
                builder.methods(httpMethods);
            }

            RequestMappingInfo newMapping = builder.build();

            // Register new mapping
            try {
                handlerMapping.registerMapping(newMapping, handlerMethod.getBean(), method);
                logger.debug("Registered ExtMapping: {} -> {}", prefixedPaths, method.getName());
            } catch (IllegalStateException e) {
                logger.warn("Mapping conflict for: {} → {}", prefixedPaths, e.getMessage());
            }
        });
    }

    // Orjinal pathleri al sipring 5 ve spring 6 farklılık gösteriyor
    private Set<String> extractPaths(RequestMappingInfo info) {
        var pathPatternsCondition = info.getPathPatternsCondition(); //Nullable anatasyonu olduğu için null olabilir, ayrı değişkene atıldı
        var patternsCondition = info.getPatternsCondition();
        if (pathPatternsCondition != null ) {
            return pathPatternsCondition.getPatternValues();
        } else if (patternsCondition != null) {
            return patternsCondition.getPatterns();
        }
        return Collections.emptySet();
    }

    private RequestMethod[] extractHttpMethods(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? new RequestMethod[0] : methods.toArray(new RequestMethod[0]);
    }
}
