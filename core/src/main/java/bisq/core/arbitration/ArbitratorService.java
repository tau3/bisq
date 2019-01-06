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

package bisq.core.arbitration;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;
import bisq.core.app.BisqEnvironment;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to store arbitrators profile and load map of arbitrators
 */
public class ArbitratorService {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorService.class);

    private final P2PService p2PService;
    private final FilterManager filterManager;

    interface ArbitratorMapResultHandler {
        void handleResult(Map<String, Arbitrator> arbitratorsMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorService(P2PService p2PService, FilterManager filterManager) {
        this.p2PService = p2PService;
        this.filterManager = filterManager;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addArbitrator(Arbitrator arbitrator, final ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("addArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        if (!BisqEnvironment.getBaseCurrencyNetwork().isMainnet() ||
                !Utilities.encodeToHex(arbitrator.getRegistrationPubKey()).equals(DevEnv.DEV_PRIVILEGE_PUB_KEY)) {
            boolean result = p2PService.addProtectedStorageEntry(arbitrator, true);
            if (result) {
                log.trace("Add arbitrator to network was successful. Arbitrator.hashCode() = " + arbitrator.hashCode());
                resultHandler.handleResult();
            } else {
                errorMessageHandler.handleErrorMessage("Add arbitrator failed");
            }
        } else {
            log.error("Attempt to publish dev arbitrator on mainnet.");
            errorMessageHandler.handleErrorMessage("Add arbitrator failed. Attempt to publish dev arbitrator on mainnet.");
        }
    }

    public void removeArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("removeArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        if (p2PService.removeData(arbitrator, true)) {
            log.trace("Remove arbitrator from network was successful. Arbitrator.hashCode() = " + arbitrator.hashCode());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Remove arbitrator failed");
        }
    }

    P2PService getP2PService() {
        return p2PService;
    }

    public final Map<NodeAddress, Arbitrator> getArbitrators() {
        Set<String> bannedHosts = getBannedHosts();
        Set<Arbitrator> arbitrators = getAllowedArbitrators(bannedHosts);
        return indexByAddress(arbitrators);
    }

    private Set<String> getBannedHosts() {
        Collection<String> bannedHosts = Optional.ofNullable(filterManager.getFilter())
                .map(Filter::getArbitrators)
                .orElse(Collections.emptyList());
        if (!bannedHosts.isEmpty()) {
            log.warn("banned arbitrators: {}", bannedHosts);
        }
        return new HashSet<>(bannedHosts);
    }

    private Set<Arbitrator> getAllowedArbitrators(Collection<String> bannedHosts) {
        Map<?, ProtectedStorageEntry> data = p2PService.getDataMap();
        Collection<ProtectedStorageEntry> entries = data.values();
        return entries.stream()
                .map(ProtectedStorageEntry::getProtectedStoragePayload)
                .map(Arbitrator.class::cast)
                .filter(Objects::nonNull)
                .filter(arbitrator -> !isBanned(arbitrator.getNodeAddress(), bannedHosts))
                .collect(Collectors.toSet());
    }

    private static boolean isBanned(NodeAddress address, Collection<String> bannedHosts) {
        String host = address.getHostName();
        return bannedHosts.contains(host);
    }

    private static Map<NodeAddress, Arbitrator> indexByAddress(Iterable<Arbitrator> arbitrators) {
        Map<NodeAddress, Arbitrator> result = new HashMap<>();
        for (Arbitrator arbitrator : arbitrators) {
            NodeAddress address = arbitrator.getNodeAddress();
            if (!result.containsKey(address)) {
                result.put(address, arbitrator);
            } else {
                log.warn("arbitratorAddress {} already exists in arbitrator map. Seems an arbitrator object is already "
                        + "registered with the same address.", address);
            }
        }

        return result;
    }
}
