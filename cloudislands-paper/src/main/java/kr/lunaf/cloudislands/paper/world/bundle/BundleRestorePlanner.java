package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;

public final class BundleRestorePlanner {
    private final BundleExtractor extractor;

    public BundleRestorePlanner(BundleExtractor extractor) {
        this.extractor = extractor;
    }

    public BundleRestorePlan plan(IslandWorldRestorer.RestorePlan restorePlan) throws IOException {
        BundleExtractor.ExtractedBundle extracted = extractor.extract(restorePlan.stagedBundle(), restorePlan.stagedBundle().getParent().resolve("extracted"));
        return new BundleRestorePlan(
            restorePlan.islandId(),
            restorePlan.worldName(),
            restorePlan.originX(),
            restorePlan.originZ(),
            restorePlan.stagedBundle(),
            extracted.rootDirectory(),
            extracted.chunksDirectory()
        );
    }
}
