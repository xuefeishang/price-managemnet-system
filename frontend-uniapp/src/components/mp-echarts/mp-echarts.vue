<template>
  <view class="echarts-wrap">
    <canvas
      v-if="isReady"
      type="2d"
      class="echarts-canvas"
      :canvas-id="canvasId"
      :id="canvasId"
    ></canvas>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, getCurrentInstance } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  option?: any
}>()

const instance = getCurrentInstance()
const canvasId = ref(`echarts-${Date.now()}-${Math.random().toString(36).slice(2)}`)
const isReady = ref(false)
let chart: echarts.ECharts | null = null

const initChart = () => {
  // #ifdef MP-WEIXIN
  const query = uni.createSelectorQuery().in(instance)
  query
    .select(`#${canvasId.value}`)
    .fields({ node: true, size: true }, () => {})
    .exec((res) => {
      if (!res || !res[0]) {
        console.warn('Canvas not found:', canvasId.value)
        return
      }

      const canvasNode = res[0].node
      if (!canvasNode) {
        console.warn('Canvas node is null')
        return
      }

      const ctx = canvasNode.getContext('2d')
      const dpr = uni.getSystemInfoSync().pixelRatio || 2

      canvasNode.width = res[0].width * dpr
      canvasNode.height = res[0].height * dpr
      ctx.scale(dpr, dpr)

      echarts.setPlatformAPI({
        createCanvas: () => canvasNode,
        createImage: () => canvasNode.createImage(),
        loadImage: (src: string, onload: Function, onerror: Function) => {
          const img = canvasNode.createImage()
          img.onload = onload
          img.onerror = onerror
          img.src = src
          return img
        }
      } as any)

      chart = echarts.init(canvasNode, undefined, {
        width: res[0].width,
        height: res[0].height,
        devicePixelRatio: dpr
      })

      if (props.option) {
        chart.setOption(props.option)
      }
    })
  // #endif

  // #ifdef H5
  setTimeout(() => {
    const dom = document.getElementById(canvasId.value)
    if (dom) {
      chart = echarts.init(dom as any)
      if (props.option) {
        chart.setOption(props.option)
      }
    }
  }, 50)
  // #endif
}

const setOption = (option: any) => {
  if (chart) {
    chart.setOption(option)
  }
}

watch(
  () => props.option,
  (newOption) => {
    if (chart && newOption) {
      chart.setOption(newOption)
    }
  },
  { deep: true }
)

onMounted(() => {
  isReady.value = true
  setTimeout(() => {
    initChart()
  }, 200)
})

onUnmounted(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})

defineExpose({
  setOption,
  chart
})
</script>

<style scoped>
.echarts-wrap {
  width: 100%;
  height: 100%;
}

.echarts-canvas {
  width: 100%;
  height: 100%;
}
</style>
