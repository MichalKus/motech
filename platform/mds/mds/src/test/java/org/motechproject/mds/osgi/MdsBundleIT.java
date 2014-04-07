package org.motechproject.mds.osgi;

import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.eclipse.gemini.blueprint.util.OsgiBundleUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.mds.dto.EntityDto;
import org.motechproject.mds.dto.FieldBasicDto;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.dto.SettingDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.service.EntityService;
import org.motechproject.mds.service.MotechDataService;
import org.motechproject.mds.util.ClassName;
import org.motechproject.mds.util.Constants;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.helper.ServiceRetriever;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.context.WebApplicationContext;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.motechproject.mds.util.Constants.BundleNames.MDS_BUNDLE_SYMBOLIC_NAME;
import static org.motechproject.mds.util.Constants.BundleNames.MDS_ENTITIES_SYMBOLIC_NAME;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MdsBundleIT extends BasePaxIT {
    private static final Logger logger = LoggerFactory.getLogger(MdsBundleIT.class);

    private static final String FOO = "Foo";
    private static final String FOO_CLASS = String.format("%s.%s", Constants.PackagesGenerated.ENTITY, FOO);

    private EntityService entityService;

    @Inject
    private BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
        WebApplicationContext context = ServiceRetriever.getWebAppContext(bundleContext, MDS_BUNDLE_SYMBOLIC_NAME);

        entityService = context.getBean(EntityService.class);

        clearEntities();
        setUpSecurityContext();
    }

    @After
    public void tearDown() throws Exception {
        clearEntities();
    }

    @Test
    public void testEntitiesBundleInstallsProperly() throws NotFoundException, CannotCompileException, IOException, InvalidSyntaxException, InterruptedException, ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        final String serviceName = ClassName.getInterfaceName(FOO_CLASS);

        prepareTestEntities();

        Bundle entitiesBundle = OsgiBundleUtils.findBundleBySymbolicName(bundleContext, MDS_ENTITIES_SYMBOLIC_NAME);
        assertNotNull(entitiesBundle);

        MotechDataService service = (MotechDataService) ServiceRetriever.getService(bundleContext, serviceName);
        Class<?> objectClass = entitiesBundle.loadClass(FOO_CLASS);
        logger.info("Loaded class: " + objectClass.getName());

        verifyInstanceCreatingAndRetrieving(service, objectClass);
        verifyInstanceUpdating(service);
        verifyInstanceDeleting(service);
    }

    private void verifyInstanceCreatingAndRetrieving(MotechDataService service, Class<?> loadedClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Object instance = loadedClass.newInstance();
        Object instance2 = loadedClass.newInstance();

        MethodUtils.invokeMethod(instance, "setSomeString", "testString1");
        MethodUtils.invokeMethod(instance, "setSomeBoolean", true);
        MethodUtils.invokeMethod(instance, "setSomeList", Arrays.asList(1, 2, 3));

        Map<String, TestClass> testMap = new HashMap<>();
        testMap.put("key1", new TestClass(123, "abc"));
        testMap.put("key2", new TestClass(456, "ddd"));

        MethodUtils.invokeMethod(instance, "setSomeMap", testMap);

        service.create(instance);
        Object retrieved = service.retrieveAll().get(0);
        assertEquals(1, service.retrieveAll().size());
        service.create(instance2);
        assertEquals(2, service.retrieveAll().size());

        assertEquals(MethodUtils.invokeMethod(retrieved, "getSomeString", null), "testString1");
        assertEquals(MethodUtils.invokeMethod(retrieved, "getSomeBoolean", null), true);
        assertEquals(MethodUtils.invokeMethod(retrieved, "getSomeList", null), Arrays.asList(1, 2, 3));

        Map retrievedMap = (Map) MethodUtils.invokeMethod(retrieved, "getSomeMap", null);

        assertTrue(retrievedMap.containsKey("key1"));
        assertTrue(retrievedMap.containsKey("key2"));

        Object testClass1 = retrievedMap.get("key1");
        Object testClass2 = retrievedMap.get("key2");

        assertEquals(MethodUtils.invokeMethod(testClass1, "getSomeInt", null), testMap.get("key1").getSomeInt());
        assertEquals(MethodUtils.invokeMethod(testClass2, "getSomeString", null), testMap.get("key2").getSomeString());
    }

    private void verifyInstanceUpdating(MotechDataService service) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        List<Object> allObjects = service.retrieveAll();
        assertEquals(allObjects.size(), 2);

        Object retrieved = allObjects.get(0);

        MethodUtils.invokeMethod(retrieved, "setSomeString", "anotherString");
        MethodUtils.invokeMethod(retrieved, "setSomeBoolean", false);
        MethodUtils.invokeMethod(retrieved, "setSomeList", Arrays.asList(4, 5));

        service.update(retrieved);
        Object updated = service.retrieveAll().get(0);

        assertEquals(MethodUtils.invokeMethod(updated, "getSomeString", null), "anotherString");
        assertEquals(MethodUtils.invokeMethod(updated, "getSomeBoolean", null), false);
        assertEquals(MethodUtils.invokeMethod(updated, "getSomeList", null), Arrays.asList(4, 5));
    }

    private void verifyInstanceDeleting(MotechDataService service) throws IllegalAccessException, InstantiationException {
        List<Object> objects = service.retrieveAll();
        assertEquals(objects.size(), 2);

        service.delete(objects.get(0));
        assertEquals(service.retrieveAll().size(), 1);

        service.delete(objects.get(1));
        assertTrue(service.retrieveAll().isEmpty());
    }

    private void prepareTestEntities() throws IOException {
        EntityDto entityDto = new EntityDto(9999L, FOO);
        entityDto = entityService.createEntity(entityDto);

        List<FieldDto> fields = new ArrayList<>();
        fields.add(new FieldDto(null, entityDto.getId(),
                TypeDto.BOOLEAN,
                new FieldBasicDto("someBoolean", "someBoolean"),
                false, null));
        fields.add(new FieldDto(null, entityDto.getId(),
                TypeDto.STRING,
                new FieldBasicDto("someString", "someString"),
                false, null));
        fields.add(new FieldDto(null, entityDto.getId(),
                TypeDto.LIST,
                new FieldBasicDto("someList", "someList"),
                false, null, null, Arrays.asList(new SettingDto("test", "test", TypeDto.INTEGER, null)), null));
        fields.add(new FieldDto(null, entityDto.getId(),
                TypeDto.LONG,
                new FieldBasicDto("id", "id"),
                false, null));
        fields.add(new FieldDto(null, entityDto.getId(),
                TypeDto.MAP,
                new FieldBasicDto("someMap", "someMap"),
                false, null));


        entityService.addFields(entityDto, fields);
        entityService.commitChanges(entityDto.getId());
    }

    private void setUpSecurityContext() {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("mdsSchemaAccess");
        List<SimpleGrantedAuthority> authorities = asList(authority);

        User principal = new User("motech", "motech", authorities);

        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null);
        authentication.setAuthenticated(false);

        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);

        SecurityContextHolder.setContext(securityContext);
    }

    private void clearEntities() {
        for (EntityDto entity : entityService.listEntities()) {
            entityService.deleteEntity(entity.getId());
        }
    }

    private void updateInstance(Object instance, Boolean boolField, String stringField, List listField,
                                DateTime dateTimeField, Map map)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        PropertyUtils.setProperty(instance, "someBoolean", boolField);
        PropertyUtils.setProperty(instance, "someString", stringField);
        PropertyUtils.setProperty(instance, "someList", listField);
        PropertyUtils.setProperty(instance, "someDateTime", dateTimeField);
        PropertyUtils.setProperty(instance, "someMap", map);
    }

    private void assertInstance(Object instance, Boolean boolField, String stringField, List listField,
                                DateTime dateTimeField, Map map)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        assertNotNull(instance);
        assertEquals(boolField, PropertyUtils.getProperty(instance, "someBoolean"));
        assertEquals(stringField, PropertyUtils.getProperty(instance, "someString"));
        assertEquals(listField, PropertyUtils.getProperty(instance, "someList"));
        assertEquals(dateTimeField, PropertyUtils.getProperty(instance, "someDateTime"));
        assertEquals(map, PropertyUtils.getProperty(instance, "someMap"));
    }
}
