/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2024 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.protocol.item.component.builtin;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// comment before implementing this: why do I have to implement this mess, I just want to deserialize items
// comment after implementing this: why did I have to implement this mess, I just want to deserialize items
public class AdventureModePredicate {

    private final List<BlockPredicate> predicates;
    private final boolean showInTooltip;

    public AdventureModePredicate(List<BlockPredicate> predicates, boolean showInTooltip) {
        this.predicates = predicates;
        this.showInTooltip = showInTooltip;
    }

    public static AdventureModePredicate read(PacketWrapper<?> wrapper) {
        List<BlockPredicate> predicates = wrapper.readList(BlockPredicate::read);
        boolean showInTooltip = wrapper.readBoolean();
        return new AdventureModePredicate(predicates, showInTooltip);
    }

    public static void write(PacketWrapper<?> wrapper, AdventureModePredicate predicate) {
        wrapper.writeList(predicate.predicates, BlockPredicate::write);
        wrapper.writeBoolean(predicate.showInTooltip);
    }

    public static class BlockPredicate {

        private final Optional<GenericHolderSet> blocks;
        private final Optional<List<PropertyMatcher>> properties;
        private final Optional<NBTCompound> nbt;

        public BlockPredicate(
                Optional<GenericHolderSet> blocks,
                Optional<List<PropertyMatcher>> properties,
                Optional<NBTCompound> nbt
        ) {
            this.blocks = blocks;
            this.properties = properties;
            this.nbt = nbt;
        }

        public static BlockPredicate read(PacketWrapper<?> wrapper) {
            Optional<GenericHolderSet> blocks = Optional.ofNullable(
                    wrapper.readOptional(GenericHolderSet::read));
            Optional<List<PropertyMatcher>> properties = Optional.ofNullable(
                    wrapper.readList(PropertyMatcher::read));
            Optional<NBTCompound> nbt = Optional.ofNullable(wrapper.readNBT());
            return new BlockPredicate(blocks, properties, nbt);
        }

        public static void write(PacketWrapper<?> wrapper, BlockPredicate predicate) {
            wrapper.writeOptional(predicate.blocks.orElse(null), GenericHolderSet::write);
            wrapper.writeOptional(predicate.properties.orElse(null),
                    (ew, val) -> ew.writeList(val, PropertyMatcher::write));
            wrapper.writeOptional(predicate.nbt.orElse(null), PacketWrapper::writeNBT);
        }
    }

    public static class GenericHolderSet {

        private final @Nullable ResourceLocation tagLoc;
        private final @Nullable List<Integer> holderIds;

        public GenericHolderSet(@Nullable ResourceLocation tagLoc, @Nullable List<Integer> holderIds) {
            if (tagLoc == null && holderIds == null) {
                throw new IllegalArgumentException("Illegal generic holder set: one of tagLoc or holderIds has to be set");
            }
            this.tagLoc = tagLoc;
            this.holderIds = holderIds;
        }

        public static GenericHolderSet read(PacketWrapper<?> wrapper) {
            int count = wrapper.readVarInt() - 1;
            if (count == -1) {
                return new GenericHolderSet(wrapper.readIdentifier(), null);
            }
            List<Integer> holderIds = new ArrayList<>(Math.min(count, 65536));
            for (int i = 0; i < count; i++) {
                holderIds.add(wrapper.readVarInt());
            }
            return new GenericHolderSet(null, holderIds);
        }

        public static void write(PacketWrapper<?> wrapper, GenericHolderSet set) {
            if (set.tagLoc != null) {
                wrapper.writeByte(0);
                wrapper.writeIdentifier(set.tagLoc);
                return;
            }

            assert set.holderIds != null; // can't be null, verified in ctor
            wrapper.writeVarInt(set.holderIds.size() + 1);
            for (int holderId : set.holderIds) {
                wrapper.writeVarInt(holderId);
            }
        }
    }

    public static class PropertyMatcher {

        private final String name;
        private final ValueMatcher matcher;

        public PropertyMatcher(String name, ValueMatcher matcher) {
            this.name = name;
            this.matcher = matcher;
        }

        public static PropertyMatcher read(PacketWrapper<?> wrapper) {
            String name = wrapper.readString();
            ValueMatcher matcher = ValueMatcher.read(wrapper);
            return new PropertyMatcher(name, matcher);
        }

        public static void write(PacketWrapper<?> wrapper, PropertyMatcher matcher) {
            wrapper.writeString(matcher.name);
            ValueMatcher.write(wrapper, matcher.matcher);
        }
    }

    public interface ValueMatcher {

        static ValueMatcher read(PacketWrapper<?> wrapper) {
            if (wrapper.readBoolean()) {
                return ExactValueMatcher.read(wrapper);
            }
            return RangedValueMatcher.read(wrapper);
        }

        static void write(PacketWrapper<?> wrapper, ValueMatcher matcher) {
            if (matcher instanceof ExactValueMatcher) {
                wrapper.writeBoolean(true);
                ExactValueMatcher.write(wrapper, (ExactValueMatcher) matcher);
            } else if (matcher instanceof RangedValueMatcher) {
                wrapper.writeBoolean(false);
                RangedValueMatcher.write(wrapper, (RangedValueMatcher) matcher);
            } else {
                throw new IllegalArgumentException("Illegal matcher implementation: " + matcher);
            }
        }
    }

    public static class ExactValueMatcher implements ValueMatcher {

        private final String value;

        public ExactValueMatcher(String value) {
            this.value = value;
        }

        public static ExactValueMatcher read(PacketWrapper<?> wrapper) {
            return new ExactValueMatcher(wrapper.readString());
        }

        public static void write(PacketWrapper<?> wrapper, ExactValueMatcher matcher) {
            wrapper.writeString(matcher.value);
        }
    }

    public static class RangedValueMatcher implements ValueMatcher {

        private final Optional<String> minValue;
        private final Optional<String> maxValue;

        public RangedValueMatcher(Optional<String> minValue, Optional<String> maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        public static RangedValueMatcher read(PacketWrapper<?> wrapper) {
            return new RangedValueMatcher(
                    Optional.ofNullable(wrapper.readOptional(PacketWrapper::readString)),
                    Optional.ofNullable(wrapper.readOptional(PacketWrapper::readString))
            );
        }

        public static void write(PacketWrapper<?> wrapper, RangedValueMatcher matcher) {
            wrapper.writeOptional(matcher.minValue.orElse(null), PacketWrapper::writeString);
            wrapper.writeOptional(matcher.maxValue.orElse(null), PacketWrapper::writeString);
        }
    }
}
