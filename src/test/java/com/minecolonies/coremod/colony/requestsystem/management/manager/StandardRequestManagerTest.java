package com.minecolonies.coremod.colony.requestsystem.management.manager;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.factory.FactoryVoidInput;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.manager.RequestMappingHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.IRequestFactory;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.requester.IRequesterFactory;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverFactory;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverProvider;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.api.util.constant.Suppression;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.coremod.colony.requestsystem.init.StandardFactoryControllerInitializer;
import com.minecolonies.coremod.colony.requestsystem.requests.AbstractRequest;
import com.minecolonies.coremod.colony.requestsystem.requests.StandardRequestFactories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StandardRequestManagerTest
{

    private StandardRequestManager   requestManager;
    private IRequestResolverProvider provider;

    @Before
    public void setUp() throws Exception
    {
        Configurations.requestSystem.enableDebugLogging = true;
        requestManager = new StandardRequestManager();

        StandardFactoryControllerInitializer.onPreInit();

        StandardFactoryController.getInstance().registerNewFactory(new StringRequestableFactory());
        StandardFactoryController.getInstance().registerNewFactory(new StringRequestFactory());
        StandardFactoryController.getInstance().registerNewFactory(new StringResolverFactory());
        StandardFactoryController.getInstance().registerNewFactory(new TestRequesterFactory());

        RequestMappingHandler.registerRequestableTypeMapping(StringRequestable.class, StringRequest.class);

        provider = new TestResolvingProvider();
    }

    @After
    public void tearDown() throws Exception
    {
        requestManager = null;
        StandardFactoryController.reset();
    }

    @Test
    public void serializeNBT() throws Exception
    {
        requestManager.onProviderAddedToColony(provider);


        final StringRequestable hello = new StringRequestable("Hello");
        final StringRequestable Test2 = new StringRequestable("Test 2");
        requestManager.createRequest(TestRequester.INSTANCE, hello);
        requestManager.createRequest(TestRequester.INSTANCE, Test2);

        NBTTagCompound compound = requestManager.serializeNBT();

        assertNotNull(compound);
    }

    @Test
    public void deserializeNBT() throws Exception
    {
        requestManager.onProviderAddedToColony(provider);

        final StringRequestable hello = new StringRequestable("Hello");
        final StringRequestable Test2 = new StringRequestable("Test 2");
        requestManager.createRequest(TestRequester.INSTANCE, hello);
        requestManager.createAndAssignRequest(TestRequester.INSTANCE, Test2);

        NBTTagCompound compound = requestManager.serializeNBT();

        StandardRequestManager deserializedVariant = new StandardRequestManager();
        deserializedVariant.onProviderAddedToColony(provider);
        deserializedVariant.deserializeNBT(compound);
    }

    @Test
    public void getFactoryController() throws Exception
    {
        assertEquals(StandardFactoryController.getInstance(), requestManager.getFactoryController());
    }

    @Test
    public void createAndAssignRequest() throws Exception
    {
        requestManager.onProviderAddedToColony(provider);

        final StringRequestable requestable = new StringRequestable("Hello");
        IToken token = requestManager.createAndAssignRequest(TestRequester.INSTANCE, requestable);
        assertNotNull(token);

        IRequest<? extends StringRequestable> request = requestManager.getRequestForToken(token);
        assertNotNull(request);
        assertEquals(requestable, request.getRequest());

        requestManager.onProviderRemovedFromColony(provider);
    }

    @Test
    public void updateRequestState() throws Exception
    {
        requestManager.onProviderAddedToColony(provider);

        final StringRequestable hello = new StringRequestable("Hello");
        IToken token = requestManager.createAndAssignRequest(TestRequester.INSTANCE, hello);

        RequestState originalState = requestManager.getRequestForToken(token).getState();
        assertEquals(RequestState.COMPLETED, originalState);

        requestManager.updateRequestState(token, RequestState.RECEIVED);
        assertNull(requestManager.getRequestForToken(token));
    }

    @Test
    public void onProviderModificationTest() throws Exception
    {
        requestManager.onProviderAddedToColony(provider);
        requestManager.onProviderRemovedFromColony(provider);
    }

    @Test
    public void reassignRequest()
    {
        requestManager.onProviderAddedToColony(provider);

    }

    private static class TestResolvingProvider implements IRequestResolverProvider
    {

        private final IToken                                token;
        private final ImmutableCollection<IRequestResolver> resolvers;

        private TestResolvingProvider()
        {
            token = StandardFactoryController.getInstance().getNewInstance(TypeConstants.ITOKEN);
            resolvers = ImmutableList.of(new StringResolver());
        }

        @Override
        public IToken getToken()
        {
            return token;
        }

        @Override
        public ImmutableCollection<IRequestResolver> getResolvers()
        {
            return resolvers;
        }
    }

    private static class StringRequest extends AbstractRequest<StringRequestable>
    {

        StringRequest(@NotNull final IRequester requester, @NotNull final IToken token, @NotNull final StringRequestable requested)
        {
            super(requester, token, requested);
        }

        StringRequest(@NotNull final IRequester requester, @NotNull final IToken token, @NotNull final RequestState state, @NotNull final StringRequestable requested)
        {
            super(requester, token, state, requested);
        }

        @NotNull
        @Override
        public ITextComponent getShortDisplayString()
        {
            return null;
        }

        @Override
        public List<ItemStack> getDisplayStacks()
        {
            return null;
        }
    }

    private static class StringRequestFactory implements IRequestFactory<StringRequestable, StringRequest>
    {

        @Override
        public StringRequest getNewInstance(
                                             @NotNull final StringRequestable input,
                                             @NotNull final IRequester location,
                                             @NotNull final IToken token,
                                             @NotNull final RequestState initialState)
        {
            return new StringRequest(location, token, initialState, input);
        }

        @NotNull
        @Override
        @SuppressWarnings(Suppression.LEFT_CURLY_BRACE)
        public TypeToken<StringRequest> getFactoryOutputType()
        {
            return TypeToken.of(StringRequest.class);
        }

        @NotNull
        @Override
        @SuppressWarnings(Suppression.LEFT_CURLY_BRACE)
        public TypeToken<StringRequestable> getFactoryInputType()
        {
            return TypeToken.of(StringRequestable.class);
        }

        @NotNull
        @Override
        public NBTTagCompound serialize(@NotNull final IFactoryController controller, @NotNull final StringRequest request)
        {
            return StandardRequestFactories.serializeToNBT(controller, request, (controller1, object) -> {
                NBTTagCompound compound = new NBTTagCompound();
                compound.setTag("String", controller.serialize(request.getRequest()));
                return compound;
            });
        }

        @NotNull
        @Override
        @SuppressWarnings(Suppression.LEFT_CURLY_BRACE)
        public StringRequest deserialize(@NotNull final IFactoryController controller, @NotNull final NBTTagCompound nbt)
        {
            return StandardRequestFactories.deserializeFromNBT(controller, nbt, ((controller1, compound) -> controller1.deserialize(compound.getCompoundTag("String"))),
              (requested, token, requester, requestState) -> controller.getNewInstance(TypeToken.of(StringRequest.class), requested, token, requester, requestState));
        }
    }

    private static class StringRequestable implements IRequestable
    {
        final String content;

        private StringRequestable(final String content) {this.content = content;}

        @Override
        public int hashCode()
        {
            return content != null ? content.hashCode() : 0;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            final StringRequestable that = (StringRequestable) o;

            return content != null ? content.equals(that.content) : that.content == null;
        }
    }

    private static class StringRequestableFactory implements IFactory<String, StringRequestable>
    {

        @NotNull
        @Override
        public TypeToken<? extends StringRequestable> getFactoryOutputType()
        {
            return TypeToken.of(StringRequestable.class);
        }

        @NotNull
        @Override
        public TypeToken<? extends String> getFactoryInputType()
        {
            return TypeToken.of(String.class);
        }

        @NotNull
        @Override
        public StringRequestable getNewInstance(@NotNull final IFactoryController factoryController, @NotNull final String s, @NotNull final Object... context)
          throws IllegalArgumentException
        {
            return new StringRequestable(s);
        }

        @NotNull
        @Override
        public NBTTagCompound serialize(
                                         @NotNull final IFactoryController controller, @NotNull final StringRequestable stringRequestable)
        {
            final NBTTagCompound compound = new NBTTagCompound();
            compound.setString("s", stringRequestable.content);
            return compound;
        }

        @NotNull
        @Override
        public StringRequestable deserialize(@NotNull final IFactoryController controller, @NotNull final NBTTagCompound nbt)
        {
            return new StringRequestable(nbt.getString("s"));
        }
    }

    private static class StringResolver implements IRequestResolver<StringRequestable>
    {

        @Override
        public TypeToken<? extends StringRequestable> getRequestType()
        {
            return TypeToken.of(StringRequestable.class);
        }

        @Override
        public boolean canResolve(@NotNull final IRequestManager manager, final IRequest<? extends StringRequestable> requestToCheck)
        {
            return true;
        }

        @Nullable
        @Override
        public List<IToken> attemptResolve(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends StringRequestable> request)
        {
            if (request.getRequest().content.length() == 1)
            {
                return Lists.newArrayList();
            }
            else
            {
                return Lists.newArrayList(manager.createRequest(TestRequester.INSTANCE, new StringRequestable(request.getRequest().content.substring(1))));
            }
        }

        @Nullable
        @Override
        public void resolve(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends StringRequestable> request) throws RuntimeException
        {
            System.out.println(request.getRequest().content);
            manager.updateRequestState(request.getToken(), RequestState.COMPLETED);
        }

        @Nullable
        @Override
        public IRequest getFollowupRequestForCompletion(
                                                         @NotNull final IRequestManager manager, @NotNull final IRequest<? extends StringRequestable> completedRequest)
        {
            return null;
        }

        @Nullable
        @Override
        public IRequest onRequestCancelledOrOverruled(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends StringRequestable> request)
          throws IllegalArgumentException
        {
            return null;
        }

        @Override
        public int getPriority()
        {
            return 0;
        }

        @Override
        public IToken getRequesterId()
        {
            return TestRequester.INSTANCE.token;
        }

        @NotNull
        @Override
        public ILocation getRequesterLocation()
        {
            return TestRequester.INSTANCE.getRequesterLocation();
        }

        @NotNull
        @Override
        public void onRequestComplete(@NotNull final IToken token)
        {
            //NOOP
        }

        @NotNull
        @Override
        public void onRequestCancelled(@NotNull final IToken token)
        {
            //NOOP
        }

        @NotNull
        @Override
        public ITextComponent getDisplayName(@NotNull final IToken token)
        {
            //Not used in test.
            return null;
        }
    }

    private static class StringResolverFactory implements IRequestResolverFactory<StringResolver>
    {

        @NotNull
        @Override
        public TypeToken<? extends StringResolver> getFactoryOutputType()
        {
            return TypeToken.of(StringResolver.class);
        }

        @NotNull
        @Override
        public TypeToken<? extends ILocation> getFactoryInputType()
        {
            return TypeConstants.ILOCATION;
        }

        @NotNull
        @Override
        public StringResolver getNewInstance(@NotNull final IFactoryController factoryController, @NotNull final ILocation iLocation, @NotNull final Object... context)
          throws IllegalArgumentException
        {
            return new StringResolver();
        }

        @NotNull
        @Override
        public NBTTagCompound serialize(@NotNull final IFactoryController controller, @NotNull final StringResolver stackResolver)
        {
            return new NBTTagCompound();
        }

        @NotNull
        @Override
        public StringResolver deserialize(@NotNull final IFactoryController controller, @NotNull final NBTTagCompound nbt)
        {
            return new StringResolver();
        }
    }

    private static class TestRequester implements IRequester
    {

        static final TestRequester INSTANCE = new TestRequester();

        private final IToken token;

        private TestRequester()
        {
            this(StandardFactoryController.getInstance().getNewInstance(TypeConstants.ITOKEN));
        }

        private TestRequester(final IToken token) {this.token = token;}

        @Override
        public IToken getRequesterId()
        {
            return token;
        }

        @NotNull
        @Override
        public ILocation getRequesterLocation()
        {
            return null;
        }

        @NotNull
        @Override
        public void onRequestComplete(@NotNull final IToken token)
        {
            return;
        }

        @NotNull
        @Override
        public void onRequestCancelled(@NotNull final IToken token)
        {
            return;
        }

        @NotNull
        @Override
        public ITextComponent getDisplayName(@NotNull final IToken token)
        {
            return new TextComponentString("Test Requester");
        }
    }

    private static class TestRequesterFactory implements IRequesterFactory<FactoryVoidInput, TestRequester>
    {

        @NotNull
        @Override
        public TypeToken<? extends TestRequester> getFactoryOutputType()
        {
            return TypeToken.of(TestRequester.class);
        }

        @NotNull
        @Override
        public TypeToken<? extends FactoryVoidInput> getFactoryInputType()
        {
            return TypeConstants.FACTORYVOIDINPUT;
        }

        @NotNull
        @Override
        public TestRequester getNewInstance(
                                             @NotNull final IFactoryController factoryController,
                                             @NotNull final FactoryVoidInput factoryVoidInput,
                                             @NotNull final Object... context) throws IllegalArgumentException
        {
            return new TestRequester();
        }

        @NotNull
        @Override
        public NBTTagCompound serialize(@NotNull final IFactoryController controller, @NotNull final TestRequester testRequester)
        {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setTag("Token", controller.serialize(testRequester.token));
            return compound;
        }

        @NotNull
        @Override
        public TestRequester deserialize(@NotNull final IFactoryController controller, @NotNull final NBTTagCompound nbt)
        {
            IToken token = controller.deserialize(nbt.getCompoundTag("Token"));
            return new TestRequester(token);
        }
    }
}