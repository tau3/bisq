package bisq.core.arbitration;

import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage.ByteArray;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Filter.class, Arbitrator.class, NodeAddress.class, ByteArray.class})
public class ArbitratorServiceTest {
    @Test
    @SuppressWarnings("ConstantConditions")
    public void testGetArbitrators() {
        // given
        List<String> bannedHosts = Lists.newArrayList("aaa", "bbb", "ccc");

        FilterManager filterManager = mock(FilterManager.class, RETURNS_DEEP_STUBS);
        when(filterManager.getFilter().getArbitrators()).thenReturn(bannedHosts);

        Map<ByteArray, ProtectedStorageEntry> data = buildFakeDataMap(Lists.newArrayList("aaa", "bbb", "ddd", "eee"));
        P2PService p2pService = mock(P2PService.class);
        when(p2pService.getDataMap()).thenReturn(data);

        ArbitratorService service = new ArbitratorService(p2pService, filterManager);

        // when
        Map<NodeAddress, Arbitrator> arbitrators = service.getArbitrators();

        // then
        Set<String> actual = arbitrators.keySet()
                .stream()
                .map(NodeAddress::getHostName)
                .collect(Collectors.toSet());

        Set<String> expected = Sets.newHashSet("ddd", "eee");

        assertThat("unexpected list of allowed arbitrators", actual, is(expected));
    }

    private static Arbitrator mockArbitrator(String host) {
        Arbitrator result = mock(Arbitrator.class, RETURNS_DEEP_STUBS);
        when(result.getNodeAddress().getHostName()).thenReturn(host);
        return result;
    }

    private static Map<ByteArray, ProtectedStorageEntry> buildFakeDataMap(Iterable<String> hosts) {
        Map<ByteArray, ProtectedStorageEntry> result = new HashMap<>();
        for (String host : hosts) {
            Arbitrator arbitrator = mockArbitrator(host);

            ByteArray key = mock(ByteArray.class);

            ProtectedStorageEntry value = mock(ProtectedStorageEntry.class);
            when(value.getProtectedStoragePayload()).thenReturn(arbitrator);

            result.put(key, value);
        }
        return result;
    }
}
