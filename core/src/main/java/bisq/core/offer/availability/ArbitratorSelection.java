/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer.availability;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.network.p2p.NodeAddress;
import javafx.collections.ObservableMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public final class ArbitratorSelection {
    public static Arbitrator getLeastUsedArbitrator(TradeStatisticsManager statsManager,
                                                    ArbitratorManager arbitratorManager) {
        List<String> lastAddresses = lastUsedAddresses(statsManager);

        ObservableMap<NodeAddress, Arbitrator> nodeToArbitrator = arbitratorManager.getArbitratorsObservableMap();
        Collection<Arbitrator> allArbitrators = nodeToArbitrator.values();
        Set<String> arbitratorAddresses = allArbitrators.stream()
                .map(Arbitrator::getNodeAddress)
                .map(NodeAddress::getHostName)
                .collect(Collectors.toSet());

        String leastUsedArbitrator = getLeastUsedArbitrator(lastAddresses, arbitratorAddresses);

        return allArbitrators.stream()
                .filter(e -> e.getNodeAddress().getHostName().equals(leastUsedArbitrator))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("optionalArbitrator has to be present"));
    }

    private static List<String> lastUsedAddresses(TradeStatisticsManager statsManager) {
        // We take last 100 entries from trade statistics
        List<TradeStatistics2> lastStats = statsManager.getObservableTradeStatisticsSet()
                .stream()
                .sorted(Comparator.comparing(TradeStatistics2::getTradeDate).reversed())
                .limit(100L)
                .collect(Collectors.toList());

        // We stored only first 4 chars of arbitrators onion address
        return lastStats.stream()
                .map(TradeStatistics2::getExtraDataMap)
                .filter(Objects::nonNull)
                .map(data -> data.get(TradeStatistics2.ARBITRATOR_ADDRESS))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    static String getLeastUsedArbitrator(Iterable<String> addresses, Collection<String> arbitratorAddresses) {
        Map<String, Long> counts = arbitratorAddresses.stream()
                .collect(Collectors.toMap(Function.identity(), a -> 1L));
        addresses.forEach(arb -> counts.computeIfPresent(arb, (key, value) -> value + 1L));

        Comparator<Map.Entry<String, Long>> byFrequency = Comparator.comparing(Map.Entry::getValue);
        Comparator<Map.Entry<String, Long>> byName = Comparator.comparing(Map.Entry::getKey);
        return counts.entrySet()
                .stream()
                .min(byFrequency.thenComparing(byName))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("no matching arbitrator"));
    }

    private ArbitratorSelection() {
    }
}
