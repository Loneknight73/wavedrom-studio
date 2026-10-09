package wavedrom.studio

// The open documents, one per tab. There is always at least one.
class Workspace(initial: AppState, newBlank: () => AppState) {
  private var tabList = Vector(initial)
  private var current = 0

  def tabs: Vector[AppState] = tabList
  def activeIndex: Int = current
  def active: AppState = tabList(current)

  def anyDirty: Boolean = tabList.exists(_.isDirty)

  // Appends a tab and makes it the active one
  def add(tab: AppState): Unit = {
    tabList = tabList :+ tab
    current = tabList.length - 1
  }

  def select(index: Int): Unit =
    if (tabList.indices.contains(index)) current = index

  // Closes a tab. The active document stays active where possible, otherwise
  // its right-hand neighbour takes over. Closing the last tab leaves a blank one.
  def close(index: Int): Unit = {
    if (tabList.indices.contains(index)) {
      tabList = tabList.patch(index, Nil, 1)
      if (tabList.isEmpty) tabList = Vector(newBlank())
      if (index < current) current -= 1
      current = Math.min(current, tabList.length - 1)
    }
  }
}
