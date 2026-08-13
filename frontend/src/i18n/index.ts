import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export const LOCALE_STORAGE_KEY = 'aiflow-locale'
export type AppLocale = 'zh-CN' | 'en-US'

export const localeOptions: { value: AppLocale; label: string }[] = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' },
]

function resolveInitialLocale(): AppLocale {
  // 中英文切换已下线，统一锁定为中文
  return 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function setLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

document.documentElement.lang = i18n.global.locale.value

export default i18n
