package net.evmodder.evmod.mixin;

import java.util.stream.Stream;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blocklist.BlockListSupplier;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AddressCheck.class)
interface MixinAddressCheck{
	// Keep vanilla's allow-all empty checker without initializing remote blocklist providers.
	@ModifyExpressionValue(method="createFromService", at=@At(value="INVOKE",
			target="Lcom/google/common/collect/Streams;stream(Ljava/lang/Iterable;)Ljava/util/stream/Stream;"))
	private static Stream<BlockListSupplier> skipBlocklistProviders(Stream<BlockListSupplier> _providers){return Stream.empty();}
}